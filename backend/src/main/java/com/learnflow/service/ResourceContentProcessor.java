package com.learnflow.service;

import com.learnflow.config.ResourceIngestionProperties;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class ResourceContentProcessor {
    public static final String PARSER_VERSION = "tika-safe-v1";
    public static final String CHUNKER_VERSION = "paragraph-pack-v1-c1200";
    private static final Pattern HTML_NO_INDEX = Pattern.compile(
            "(?is)<meta[^>]+name\\s*=\\s*['\"]?robots['\"]?[^>]+content\\s*=\\s*['\"][^'\"]*(?:noindex|noarchive)[^'\"]*['\"]"
    );
    private static final Pattern HTML_NO_INDEX_REVERSED = Pattern.compile(
            "(?is)<meta[^>]+content\\s*=\\s*['\"][^'\"]*(?:noindex|noarchive)[^'\"]*['\"][^>]+name\\s*=\\s*['\"]?robots['\"]?"
    );
    private static final Set<String> EXACT_ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/rtf"
    );

    private final ResourceIngestionProperties properties;
    private final Tika tika = new Tika();
    private final OkHttpClient client;

    public ResourceContentProcessor(ResourceIngestionProperties properties) {
        this.properties = properties;
        Duration connect = bounded(properties.getConnectTimeout(), Duration.ofSeconds(5));
        Duration read = bounded(properties.getReadTimeout(), Duration.ofSeconds(30));
        this.client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(new PublicOnlyDns())
                .connectTimeout(connect.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(read.toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(read.plus(connect).toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public ProcessedContent fetchAndProcess(String sourceUrl) throws IOException {
        URI current = validatePublicUri(sourceUrl);
        int redirects = 0;
        while (true) {
            Request request = new Request.Builder()
                    .url(current.toString())
                    .header("User-Agent", "LearnFlowResourceIndexer/1.0")
                    .header("Accept", "text/plain,text/html,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document;q=0.9")
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isRedirect()) {
                    if (++redirects > Math.max(0, properties.getMaxRedirects())) {
                        throw new ResourceIngestionException("TOO_MANY_REDIRECTS", "Resource URL exceeded redirect limit");
                    }
                    String location = response.header("Location");
                    if (location == null) throw new ResourceIngestionException("INVALID_REDIRECT", "Redirect has no target");
                    current = validatePublicUri(current.resolve(location).toString());
                    continue;
                }
                if (!response.isSuccessful()) {
                    throw new ResourceIngestionException("UPSTREAM_HTTP_STATUS", "Resource server rejected the request");
                }
                String robots = response.header("X-Robots-Tag", "").toLowerCase(Locale.ROOT);
                if (robots.contains("noindex") || robots.contains("noarchive")) {
                    throw new ResourceIngestionException("INDEXING_DISALLOWED", "Resource owner disallowed indexing");
                }
                ResponseBody body = response.body();
                if (body == null) throw new ResourceIngestionException("EMPTY_RESPONSE", "Resource response has no body");
                long declaredLength = body.contentLength();
                enforceSize(declaredLength);
                byte[] bytes = readBounded(body.byteStream());
                String responseType = body.contentType() == null ? null : body.contentType().toString();
                return process(bytes, responseType, filenameFromUri(current));
            }
        }
    }

    public ProcessedContent process(InputStream input, long declaredLength, String suppliedContentType, String filename) throws IOException {
        enforceSize(declaredLength);
        return process(readBounded(input), suppliedContentType, filename);
    }

    public ProcessedContent process(byte[] bytes, String suppliedContentType, String filename) throws IOException {
        enforceSize(bytes.length);
        String detected = tika.detect(bytes, filename == null ? "source" : filename);
        String mimeType = normalizeMimeType(detected != null ? detected : suppliedContentType);
        if (!isAllowed(mimeType)) {
            throw new ResourceIngestionException("UNSUPPORTED_CONTENT_TYPE", "Resource content type is not supported");
        }
        if ("text/html".equals(mimeType)) {
            String htmlPrefix = new String(bytes, 0, Math.min(bytes.length, 128 * 1024), StandardCharsets.UTF_8);
            if (HTML_NO_INDEX.matcher(htmlPrefix).find() || HTML_NO_INDEX_REVERSED.matcher(htmlPrefix).find()) {
                throw new ResourceIngestionException("INDEXING_DISALLOWED", "Resource page disallowed indexing");
            }
        }

        Metadata metadata = new Metadata();
        if (filename != null) metadata.set("resourceName", filename);
        String extracted;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
            extracted = tika.parseToString(stream, metadata, Math.max(1_000, properties.getMaxExtractedCharacters()));
        } catch (Exception failure) {
            throw new ResourceIngestionException("PARSE_FAILED", "Resource content could not be parsed", failure);
        }
        String normalized = normalizeText(extracted);
        if (normalized.isBlank()) throw new ResourceIngestionException("EMPTY_CONTENT", "Resource has no indexable text");
        String language = inferLanguage(normalized);
        List<Chunk> chunks = chunk(normalized);
        if (chunks.isEmpty()) throw new ResourceIngestionException("EMPTY_CONTENT", "Resource has no indexable chunks");
        return new ProcessedContent(bytes, mimeType, sha256(bytes), language, normalized.length(), chunks);
    }

    private List<Chunk> chunk(String normalized) {
        int target = Math.max(300, properties.getChunkTargetCharacters());
        int maximum = Math.max(target, properties.getChunkMaxCharacters());
        List<Chunk> result = new ArrayList<>();
        int cursor = 0;
        int ordinal = 0;
        while (cursor < normalized.length()) {
            while (cursor < normalized.length() && Character.isWhitespace(normalized.charAt(cursor))) cursor++;
            if (cursor >= normalized.length()) break;
            int desired = Math.min(normalized.length(), cursor + target);
            int hardEnd = Math.min(normalized.length(), cursor + maximum);
            int end = findBoundary(normalized, desired, hardEnd);
            String content = normalized.substring(cursor, end).trim();
            if (!content.isBlank()) {
                int actualStart = normalized.indexOf(content, cursor);
                int actualEnd = actualStart + content.length();
                result.add(new Chunk(ordinal++, actualStart, actualEnd, content, sha256(content.getBytes(StandardCharsets.UTF_8))));
                cursor = actualEnd;
            } else {
                cursor = end;
            }
        }
        return result;
    }

    private static int findBoundary(String text, int desired, int hardEnd) {
        if (desired >= text.length()) return text.length();
        for (int index = desired; index < hardEnd; index++) {
            char ch = text.charAt(index);
            if (ch == '\n' || ch == '。' || ch == '！' || ch == '？' || ch == '.' || ch == '!' || ch == '?') return index + 1;
        }
        return hardEnd;
    }

    private byte[] readBounded(InputStream input) throws IOException {
        long max = Math.max(1_024, properties.getMaxSourceBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(max, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > max) throw new ResourceIngestionException("SOURCE_TOO_LARGE", "Resource source exceeded configured limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void enforceSize(long length) {
        if (length > properties.getMaxSourceBytes()) {
            throw new ResourceIngestionException("SOURCE_TOO_LARGE", "Resource source exceeded configured limit");
        }
    }

    public static URI validatePublicUri(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if (uri.getHost() == null || uri.getUserInfo() != null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new ResourceIngestionException("UNSAFE_SOURCE_URL", "Only credential-free HTTP(S) URLs are accepted");
            }
            if (port != -1 && port != 80 && port != 443) {
                throw new ResourceIngestionException("UNSAFE_SOURCE_URL", "Resource URL port is not allowed");
            }
            return uri;
        } catch (RuntimeException failure) {
            if (failure instanceof ResourceIngestionException ingestionFailure) throw ingestionFailure;
            throw new ResourceIngestionException("UNSAFE_SOURCE_URL", "Resource URL is invalid", failure);
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 0xff, b = bytes[1] & 0xff, c = bytes[2] & 0xff;
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && b == 0)
                    && !(a == 192 && b == 168)
                    && !(a == 192 && b == 0 && c == 2)
                    && !(a == 198 && (b == 18 || b == 19))
                    && !(a == 198 && b == 51 && c == 100)
                    && !(a == 203 && b == 0 && c == 113);
        }
        int first = bytes[0] & 0xff;
        return (first & 0xfe) != 0xfc && !(bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && (bytes[3] & 0xff) == 0xb8);
    }

    private static String normalizeText(String value) {
        return value.replace("\u0000", "")
                .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\r\\n?", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String inferLanguage(String value) {
        int sampled = 0, cjk = 0;
        for (int index = 0; index < value.length() && sampled < 10_000; index++) {
            char ch = value.charAt(index);
            if (!Character.isLetter(ch)) continue;
            sampled++;
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) cjk++;
        }
        return sampled > 0 && cjk * 4 >= sampled ? "zh" : "en";
    }

    private static boolean isAllowed(String type) { return type.startsWith("text/") || EXACT_ALLOWED_TYPES.contains(type); }
    private static String normalizeMimeType(String value) { return value == null ? "application/octet-stream" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT); }
    private static String filenameFromUri(URI uri) { String path = uri.getPath(); return path == null || path.endsWith("/") ? "resource" : path.substring(path.lastIndexOf('/') + 1); }
    private static Duration bounded(Duration value, Duration fallback) { return value == null || value.isNegative() || value.isZero() ? fallback : value; }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }

    private static final class PublicOnlyDns implements Dns {
        @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
            if (addresses.isEmpty() || addresses.stream().anyMatch(address -> !isPublicAddress(address))) {
                throw new UnknownHostException("Resource host did not resolve exclusively to public addresses");
            }
            return addresses;
        }
    }

    public record Chunk(int ordinal, int charStart, int charEnd, String content, String contentHash) {}
    public record ProcessedContent(byte[] sourceBytes, String contentType, String contentSha256, String language, int extractedCharacters, List<Chunk> chunks) {}
}

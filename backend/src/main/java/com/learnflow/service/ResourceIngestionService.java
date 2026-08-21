package com.learnflow.service;

import com.learnflow.config.ResourceIngestionProperties;
import com.learnflow.dto.ResourceIngestionResponse;
import com.learnflow.dto.ResourceIngestionStatusResponse;
import com.learnflow.dto.TextResourceIngestionRequest;
import com.learnflow.dto.UrlResourceIngestionRequest;
import com.learnflow.entity.ResourceIngestion;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResourceIngestionService {
    private final ResourceIngestionProperties properties;
    private final ResourceIngestionPersistenceService persistence;
    private final ResourceContentProcessor processor;
    private final ResourceSourceStore sourceStore;
    private final AsyncTaskService tasks;
    private final MeterRegistry meterRegistry;

    public ResourceIngestionService(ResourceIngestionProperties properties,
                                    ResourceIngestionPersistenceService persistence,
                                    ResourceContentProcessor processor,
                                    ResourceSourceStore sourceStore,
                                    AsyncTaskService tasks,
                                    MeterRegistry meterRegistry) {
        this.properties = properties;
        this.persistence = persistence;
        this.processor = processor;
        this.sourceStore = sourceStore;
        this.tasks = tasks;
        this.meterRegistry = meterRegistry;
    }

    public ResourceIngestionResponse submitUrl(UrlResourceIngestionRequest request, long userId, String username, String idempotencyKey) {
        ensureEnabled();
        String url = ResourceContentProcessor.validatePublicUri(request.url()).toString();
        String fingerprint = sha256(canonical(request.title(), request.domain(), request.level(), request.tags(), url));
        ResourceIngestionPersistenceService.PendingIngestion pending = persistence.createPending(
                request.title(), url, request.domain(), request.level(), request.durationMinutes(), request.tags(),
                "URL", url, null, userId, username
        );
        return submitTask(pending, userId, idempotencyKey, fingerprint, null);
    }

    public ResourceIngestionResponse submitText(TextResourceIngestionRequest request, long userId, String username, String idempotencyKey) {
        ensureEnabled();
        byte[] bytes = request.text().getBytes(StandardCharsets.UTF_8);
        validateUpload(bytes.length, "text/plain");
        String contentHash = sha256(bytes);
        String fingerprint = sha256(canonical(request.title(), request.domain(), request.level(), request.tags(), contentHash));
        ResourceIngestionPersistenceService.PendingIngestion pending = persistence.createPending(
                request.title(), null, request.domain(), request.level(), request.durationMinutes(), request.tags(),
                "TEXT", "inline-text:" + contentHash, request.title() + ".txt", userId, username
        );
        String key = store(pending, bytes, "text/plain; charset=utf-8");
        return submitTask(pending, userId, idempotencyKey, fingerprint, key);
    }

    public ResourceIngestionResponse submitDocument(String title, String domain, String level, Integer durationMinutes,
                                                     String tags, MultipartFile file, long userId, String username,
                                                     String idempotencyKey) {
        ensureEnabled();
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文档不能为空");
        if (title == null || title.isBlank() || title.length() > 300 || domain == null || domain.isBlank() || domain.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题或领域无效");
        }
        if (file.getSize() > properties.getMaxSourceBytes()) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文档超过大小限制");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException failure) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取文档", failure); }
        String suppliedType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        validateUpload(bytes.length, suppliedType);
        String contentHash = sha256(bytes);
        String fingerprint = sha256(canonical(title, domain, level, tags, contentHash));
        ResourceIngestionPersistenceService.PendingIngestion pending = persistence.createPending(
                title, null, domain, level, durationMinutes, tags, "DOCUMENT", "uploaded-document:" + contentHash,
                file.getOriginalFilename(), userId, username
        );
        String key = store(pending, bytes, suppliedType);
        return submitTask(pending, userId, idempotencyKey, fingerprint, key);
    }

    public ResourceIngestionResponse submitUrlVersion(long resourceId, String url, long adminUserId, String idempotencyKey) {
        ensureEnabled();
        String normalizedUrl = ResourceContentProcessor.validatePublicUri(url).toString();
        String fingerprint = sha256(canonical(resourceId, normalizedUrl));
        ResourceIngestionPersistenceService.PendingIngestion pending = persistence.createVersion(resourceId, "URL", normalizedUrl, null);
        return submitVersionTask(pending, adminUserId, idempotencyKey, fingerprint, null);
    }

    public ResourceIngestionResponse submitTextVersion(long resourceId, String text, long adminUserId, String idempotencyKey) {
        ensureEnabled();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        validateUpload(bytes.length, "text/plain");
        String hash = sha256(bytes);
        ResourceIngestionPersistenceService.PendingIngestion pending = persistence.createVersion(resourceId, "TEXT", "inline-text:" + hash, "resource-" + resourceId + ".txt");
        String key = store(pending, bytes, "text/plain; charset=utf-8");
        return submitVersionTask(pending, adminUserId, idempotencyKey, sha256(canonical(resourceId, hash)), key);
    }

    public ResourceIngestionResponse submitDocumentVersion(long resourceId, MultipartFile file, long adminUserId, String idempotencyKey) {
        ensureEnabled();
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文档不能为空");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException failure) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取文档", failure); }
        validateUpload(bytes.length, file.getContentType());
        String hash = sha256(bytes);
        ResourceIngestionPersistenceService.PendingIngestion pending = persistence.createVersion(
                resourceId, "DOCUMENT", "uploaded-document:" + hash, file.getOriginalFilename()
        );
        String key = store(pending, bytes, file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        return submitVersionTask(pending, adminUserId, idempotencyKey, sha256(canonical(resourceId, hash)), key);
    }

    public long process(UUID ingestionId) throws IOException {
        long started = System.nanoTime();
        ResourceIngestion ingestion = persistence.markProcessing(ingestionId);
        if ("SUCCEEDED".equals(ingestion.getStatus())) return ingestion.getResourceId();
        try {
            ResourceContentProcessor.ProcessedContent processed;
            String objectKey = ingestion.getObjectKey();
            if ("URL".equals(ingestion.getSourceType())) {
                processed = processor.fetchAndProcess(ingestion.getSourceLocator());
                objectKey = objectKey(ingestion.getResourceId(), ingestionId, processed.contentSha256());
                sourceStore.put(objectKey, new ByteArrayInputStream(processed.sourceBytes()), processed.sourceBytes().length, processed.contentType());
            } else {
                if (objectKey == null) throw new ResourceIngestionException("SOURCE_OBJECT_MISSING", "Stored source reference is missing");
                try (InputStream input = sourceStore.open(objectKey)) {
                    processed = processor.process(input, ingestion.getContentLength() == null ? -1 : ingestion.getContentLength(),
                            ingestion.getContentType(), ingestion.getOriginalFilename());
                }
            }
            long resourceId = persistence.complete(ingestionId, processed, objectKey);
            record(ingestion.getSourceType(), "success", "none", started, processed.sourceBytes().length, processed.chunks().size());
            return resourceId;
        } catch (Exception failure) {
            String code = failure instanceof ResourceIngestionException ingestionFailure
                    ? ingestionFailure.getCode() : failure.getClass().getSimpleName().toUpperCase(Locale.ROOT);
            persistence.markFailed(ingestionId, code);
            record(ingestion.getSourceType(), "failure", code, started, 0, 0);
            if (failure instanceof IOException ioFailure) throw ioFailure;
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            throw new IOException("Resource ingestion failed", failure);
        }
    }

    public ResourceIngestionStatusResponse getStatus(UUID ingestionId, long requesterId, boolean admin) {
        return persistence.getStatus(ingestionId, requesterId, admin);
    }

    private ResourceIngestionResponse submitTask(ResourceIngestionPersistenceService.PendingIngestion pending, long userId,
                                                 String requestedKey, String fingerprint, String objectKey) {
        AsyncTaskService.TaskSubmission submission;
        try {
            submission = tasks.createResourceIngestionTask(pending.ingestionId(), pending.resourceId(), userId, requestedKey, fingerprint);
        } catch (RuntimeException failure) {
            persistence.markFailed(pending.ingestionId(), "TASK_SUBMISSION_FAILED");
            throw failure;
        }
        if (submission.created()) {
            return new ResourceIngestionResponse(pending.resourceId(), pending.ingestionId(), submission.task());
        }
        persistence.discardPending(pending.ingestionId());
        if (objectKey != null) try { sourceStore.delete(objectKey); } catch (IOException ignored) { /* retry cleanup via lifecycle job */ }
        Long existingResourceId = submission.task().resultResourceId();
        UUID existingIngestionId = submission.ingestionId();
        if (existingIngestionId == null && existingResourceId != null) existingIngestionId = persistence.currentIngestionId(existingResourceId);
        return new ResourceIngestionResponse(existingResourceId, existingIngestionId, submission.task());
    }

    private ResourceIngestionResponse submitVersionTask(ResourceIngestionPersistenceService.PendingIngestion pending, long userId,
                                                         String requestedKey, String fingerprint, String objectKey) {
        AsyncTaskService.TaskSubmission submission;
        try {
            submission = tasks.createResourceIngestionTask(pending.ingestionId(), pending.resourceId(), userId, requestedKey, fingerprint);
        } catch (RuntimeException failure) {
            persistence.markFailed(pending.ingestionId(), "TASK_SUBMISSION_FAILED");
            throw failure;
        }
        if (submission.created()) return new ResourceIngestionResponse(pending.resourceId(), pending.ingestionId(), submission.task());
        persistence.discardVersion(pending.ingestionId());
        if (objectKey != null) try { sourceStore.delete(objectKey); } catch (IOException ignored) { /* lifecycle cleanup */ }
        UUID existingIngestionId = submission.ingestionId();
        Long existingResourceId = submission.task().resultResourceId();
        if (existingResourceId == null && existingIngestionId != null) existingResourceId = persistence.require(existingIngestionId).getResourceId();
        if (existingIngestionId == null && existingResourceId != null) existingIngestionId = persistence.currentIngestionId(existingResourceId);
        return new ResourceIngestionResponse(existingResourceId, existingIngestionId, submission.task());
    }

    private String store(ResourceIngestionPersistenceService.PendingIngestion pending, byte[] bytes, String contentType) {
        String hash = sha256(bytes);
        String key = objectKey(pending.resourceId(), pending.ingestionId(), hash);
        try {
            sourceStore.put(key, new ByteArrayInputStream(bytes), bytes.length, contentType);
            persistence.attachStoredSource(pending.ingestionId(), key, contentType, bytes.length, hash);
            return key;
        } catch (IOException failure) {
            persistence.markFailed(pending.ingestionId(), "SOURCE_STORAGE_FAILED");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "资源原件暂时无法保存", failure);
        }
    }

    private String objectKey(long resourceId, UUID ingestionId, String hash) {
        String prefix = properties.getStorage().getKeyPrefix().replaceAll("[^A-Za-z0-9/_-]", "").replaceAll("^/+|/+$", "");
        return (prefix.isBlank() ? "" : prefix + "/") + "resources/" + resourceId + "/" + ingestionId + "/" + hash + ".source";
    }

    private void validateUpload(long size, String contentType) {
        if (size <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资源内容不能为空");
        if (size > properties.getMaxSourceBytes()) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "资源超过大小限制");
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("multipart")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持嵌套 multipart 文档");
        }
    }

    private void ensureEnabled() { if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "资源摄取暂未启用"); }
    private void record(String sourceType, String outcome, String reason, long started, long bytes, int chunks) {
        String normalizedReason = reason.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        normalizedReason = normalizedReason.substring(0, Math.min(48, normalizedReason.length()));
        Tags tags = Tags.of("source.type", sourceType.toLowerCase(Locale.ROOT), "outcome", outcome,
                "reason", normalizedReason);
        meterRegistry.counter("learnflow.resource.ingestion", tags).increment();
        meterRegistry.timer("learnflow.resource.ingestion.duration", tags).record(java.time.Duration.ofNanos(System.nanoTime() - started));
        meterRegistry.summary("learnflow.resource.ingestion.bytes", tags).record(bytes);
        meterRegistry.summary("learnflow.resource.ingestion.chunks", tags).record(chunks);
    }
    private static String canonical(Object... values) { StringBuilder result = new StringBuilder(); for (Object value : values) { String text = value == null ? "" : value.toString().trim(); result.append(text.length()).append(':').append(text).append('|'); } return result.toString(); }
    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
}

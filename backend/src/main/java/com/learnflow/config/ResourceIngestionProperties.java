package com.learnflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "learnflow.resource-ingestion")
public class ResourceIngestionProperties {
    private boolean enabled = true;
    private long maxSourceBytes = 10 * 1024 * 1024;
    private int maxExtractedCharacters = 2_000_000;
    private int chunkTargetCharacters = 1_200;
    private int chunkMaxCharacters = 1_800;
    private int maxRedirects = 5;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);
    private final Storage storage = new Storage();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getMaxSourceBytes() { return maxSourceBytes; }
    public void setMaxSourceBytes(long maxSourceBytes) { this.maxSourceBytes = maxSourceBytes; }
    public int getMaxExtractedCharacters() { return maxExtractedCharacters; }
    public void setMaxExtractedCharacters(int maxExtractedCharacters) { this.maxExtractedCharacters = maxExtractedCharacters; }
    public int getChunkTargetCharacters() { return chunkTargetCharacters; }
    public void setChunkTargetCharacters(int chunkTargetCharacters) { this.chunkTargetCharacters = chunkTargetCharacters; }
    public int getChunkMaxCharacters() { return chunkMaxCharacters; }
    public void setChunkMaxCharacters(int chunkMaxCharacters) { this.chunkMaxCharacters = chunkMaxCharacters; }
    public int getMaxRedirects() { return maxRedirects; }
    public void setMaxRedirects(int maxRedirects) { this.maxRedirects = maxRedirects; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Storage getStorage() { return storage; }

    public static class Storage {
        private String type = "filesystem";
        private Path filesystemRoot = Path.of("tmp", "resource-sources");
        private String bucket = "";
        private String region = "us-east-1";
        private URI endpoint;
        private String keyPrefix = "learnflow";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Path getFilesystemRoot() { return filesystemRoot; }
        public void setFilesystemRoot(Path filesystemRoot) { this.filesystemRoot = filesystemRoot; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public URI getEndpoint() { return endpoint; }
        public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    }
}

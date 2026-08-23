package com.learnflow.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ResourceEvidenceDto {

    private UUID chunkId;
    private String excerpt;
    private String sourceUrl;
    private String contentHash;
    private List<String> retrievalChannels = new ArrayList<>();

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public List<String> getRetrievalChannels() {
        return retrievalChannels;
    }

    public void setRetrievalChannels(List<String> retrievalChannels) {
        this.retrievalChannels = retrievalChannels;
    }
}

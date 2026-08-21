package com.learnflow.service;

import com.learnflow.dto.ResourceIngestionStatusResponse;
import com.learnflow.entity.ResourceBank;
import com.learnflow.entity.ResourceChunk;
import com.learnflow.entity.ResourceIngestion;
import com.learnflow.entity.ResourceIngestionChunk;
import com.learnflow.repository.ResourceBankRepository;
import com.learnflow.repository.ResourceChunkRepository;
import com.learnflow.repository.ResourceIngestionChunkRepository;
import com.learnflow.repository.ResourceIngestionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResourceIngestionPersistenceService {
    private final ResourceBankRepository resources;
    private final ResourceIngestionRepository ingestions;
    private final ResourceChunkRepository chunks;
    private final ResourceIngestionChunkRepository ingestionChunks;

    public ResourceIngestionPersistenceService(ResourceBankRepository resources, ResourceIngestionRepository ingestions,
                                               ResourceChunkRepository chunks, ResourceIngestionChunkRepository ingestionChunks) {
        this.resources = resources;
        this.ingestions = ingestions;
        this.chunks = chunks;
        this.ingestionChunks = ingestionChunks;
    }

    @Transactional
    public PendingIngestion createPending(String title, String url, String domain, String level, Integer durationMinutes,
                                          String tags, String sourceType, String locator, String filename,
                                          Long ownerUserId, String ownerUsername) {
        ResourceBank resource = new ResourceBank();
        resource.setTitle(normalize(title));
        resource.setUrl(url);
        resource.setDomain(normalizeLower(domain));
        resource.setLevel(normalizeLower(level));
        resource.setDurationMinutes(durationMinutes);
        resource.setTags(normalize(tags));
        resource.setUploaderUserId(ownerUserId);
        resource.setUploaderUsername(ownerUsername);
        resource.setStatus("PENDING");
        resource.setSourceType(sourceType);
        resource.setIngestionStatus("PENDING");
        resource = resources.saveAndFlush(resource);

        OffsetDateTime timestamp = now();
        ResourceIngestion ingestion = new ResourceIngestion();
        ingestion.setId(UUID.randomUUID());
        ingestion.setResourceId(resource.getId());
        ingestion.setSourceType(sourceType);
        ingestion.setSourceLocator(locator);
        ingestion.setOriginalFilename(safeFilename(filename));
        ingestion.setParserVersion(ResourceContentProcessor.PARSER_VERSION);
        ingestion.setChunkerVersion(ResourceContentProcessor.CHUNKER_VERSION);
        ingestion.setRightsConfirmedAt(timestamp);
        ingestion.setStatus("PENDING");
        ingestion.setCreatedAt(timestamp);
        ingestion.setUpdatedAt(timestamp);
        ingestions.save(ingestion);
        return new PendingIngestion(resource.getId(), ingestion.getId());
    }

    @Transactional
    public PendingIngestion createVersion(long resourceId, String sourceType, String locator, String filename) {
        ResourceBank resource = resources.findById(resourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在"));
        OffsetDateTime timestamp = now();
        ResourceIngestion ingestion = new ResourceIngestion();
        ingestion.setId(UUID.randomUUID());
        ingestion.setResourceId(resourceId);
        ingestion.setSourceType(sourceType);
        ingestion.setSourceLocator(locator);
        ingestion.setOriginalFilename(safeFilename(filename));
        ingestion.setParserVersion(ResourceContentProcessor.PARSER_VERSION);
        ingestion.setChunkerVersion(ResourceContentProcessor.CHUNKER_VERSION);
        ingestion.setRightsConfirmedAt(timestamp);
        ingestion.setStatus("PENDING");
        ingestion.setCreatedAt(timestamp);
        ingestion.setUpdatedAt(timestamp);
        ingestions.save(ingestion);
        resource.setSourceType(sourceType);
        if ("URL".equals(sourceType)) resource.setUrl(locator);
        resource.setIngestionStatus("PENDING");
        resources.save(resource);
        return new PendingIngestion(resourceId, ingestion.getId());
    }

    @Transactional
    public void attachStoredSource(UUID ingestionId, String objectKey, String contentType, long length, String sha256) {
        ResourceIngestion ingestion = require(ingestionId);
        ingestion.setObjectKey(objectKey);
        ingestion.setContentType(contentType);
        ingestion.setContentLength(length);
        ingestion.setContentSha256(sha256);
        ingestion.setUpdatedAt(now());
        ingestions.save(ingestion);
    }

    @Transactional
    public ResourceIngestion markProcessing(UUID ingestionId) {
        ResourceIngestion ingestion = require(ingestionId);
        if ("SUCCEEDED".equals(ingestion.getStatus())) return ingestion;
        OffsetDateTime timestamp = now();
        ingestion.setStatus("PROCESSING");
        ingestion.setStartedAt(ingestion.getStartedAt() == null ? timestamp : ingestion.getStartedAt());
        ingestion.setErrorCode(null);
        ingestion.setUpdatedAt(timestamp);
        ResourceBank resource = resources.findById(ingestion.getResourceId()).orElseThrow();
        resource.setIngestionStatus("PROCESSING");
        resources.save(resource);
        return ingestions.save(ingestion);
    }

    @Transactional
    public long complete(UUID ingestionId, ResourceContentProcessor.ProcessedContent content, String objectKey) {
        ResourceIngestion ingestion = require(ingestionId);
        if ("SUCCEEDED".equals(ingestion.getStatus())) return ingestion.getResourceId();
        OffsetDateTime timestamp = now();
        List<String> hashes = content.chunks().stream().map(ResourceContentProcessor.Chunk::contentHash).distinct().toList();
        Map<String, ResourceChunk> byHash = new HashMap<>();
        chunks.findByResourceIdAndContentHashIn(ingestion.getResourceId(), hashes)
                .forEach(chunk -> byHash.put(chunk.getContentHash(), chunk));

        ingestionChunks.deleteByIngestionId(ingestionId);
        for (ResourceContentProcessor.Chunk processed : content.chunks()) {
            ResourceChunk chunk = byHash.get(processed.contentHash());
            if (chunk == null) {
                chunk = new ResourceChunk();
                chunk.setId(UUID.randomUUID());
                chunk.setResourceId(ingestion.getResourceId());
                chunk.setContentHash(processed.contentHash());
                chunk.setContent(processed.content());
                chunk.setLanguage(content.language());
                chunk.setCreatedAt(timestamp);
                chunk = chunks.save(chunk);
                byHash.put(chunk.getContentHash(), chunk);
            }
            ResourceIngestionChunk link = new ResourceIngestionChunk();
            link.setIngestionId(ingestionId);
            link.setChunkId(chunk.getId());
            link.setOrdinal(processed.ordinal());
            link.setCharStart(processed.charStart());
            link.setCharEnd(processed.charEnd());
            ingestionChunks.save(link);
        }

        ingestion.setObjectKey(objectKey);
        ingestion.setContentType(content.contentType());
        ingestion.setContentLength((long) content.sourceBytes().length);
        ingestion.setContentSha256(content.contentSha256());
        ingestion.setLanguage(content.language());
        ingestion.setStatus("SUCCEEDED");
        ingestion.setErrorCode(null);
        ingestion.setFinishedAt(timestamp);
        ingestion.setUpdatedAt(timestamp);
        ingestions.save(ingestion);

        ResourceBank resource = resources.findById(ingestion.getResourceId()).orElseThrow();
        resource.setCurrentIngestionId(ingestionId);
        resource.setIngestionStatus("SUCCEEDED");
        resources.save(resource);
        return resource.getId();
    }

    @Transactional
    public void markFailed(UUID ingestionId, String errorCode) {
        ResourceIngestion ingestion = require(ingestionId);
        if ("SUCCEEDED".equals(ingestion.getStatus())) return;
        OffsetDateTime timestamp = now();
        String bounded = errorCode == null ? "INGESTION_FAILED" : errorCode.replaceAll("[^A-Za-z0-9_]", "_");
        ingestion.setStatus("FAILED");
        ingestion.setErrorCode(bounded.substring(0, Math.min(64, bounded.length())));
        ingestion.setFinishedAt(timestamp);
        ingestion.setUpdatedAt(timestamp);
        ingestions.save(ingestion);
        resources.findById(ingestion.getResourceId()).ifPresent(resource -> {
            resource.setIngestionStatus("FAILED");
            resources.save(resource);
        });
    }

    @Transactional(readOnly = true)
    public ResourceIngestion require(UUID id) {
        return ingestions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "摄取记录不存在"));
    }

    @Transactional(readOnly = true)
    public ResourceIngestionStatusResponse getStatus(UUID id, long requesterId, boolean admin) {
        ResourceIngestion ingestion = require(id);
        ResourceBank resource = resources.findById(ingestion.getResourceId()).orElseThrow();
        if (!admin && !Long.valueOf(requesterId).equals(resource.getUploaderUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "摄取记录不存在");
        }
        int chunkCount = ingestionChunks.findByIngestionIdOrderByOrdinal(id).size();
        return new ResourceIngestionStatusResponse(id, ingestion.getResourceId(), ingestion.getSourceType(), ingestion.getStatus(),
                ingestion.getContentType(), ingestion.getContentLength(), ingestion.getContentSha256(), ingestion.getLanguage(),
                ingestion.getParserVersion(), ingestion.getChunkerVersion(), chunkCount, ingestion.getErrorCode(),
                ingestion.getCreatedAt(), ingestion.getStartedAt(), ingestion.getFinishedAt());
    }

    @Transactional
    public void discardPending(UUID ingestionId) {
        ResourceIngestion ingestion = require(ingestionId);
        if (!"PENDING".equals(ingestion.getStatus())) return;
        Long resourceId = ingestion.getResourceId();
        ingestions.delete(ingestion);
        resources.deleteById(resourceId);
    }

    @Transactional
    public void discardVersion(UUID ingestionId) {
        ResourceIngestion ingestion = require(ingestionId);
        if (!"PENDING".equals(ingestion.getStatus())) return;
        ResourceBank resource = resources.findById(ingestion.getResourceId()).orElseThrow();
        ingestions.delete(ingestion);
        resource.setIngestionStatus(resource.getCurrentIngestionId() == null ? "NOT_STARTED" : "SUCCEEDED");
        resources.save(resource);
    }

    @Transactional(readOnly = true)
    public UUID currentIngestionId(Long resourceId) {
        return resources.findById(resourceId).map(ResourceBank::getCurrentIngestionId).orElse(null);
    }

    private static String normalize(String value) { return value == null ? null : value.trim(); }
    private static String normalizeLower(String value) { String normalized = normalize(value); return normalized == null || normalized.isBlank() ? null : normalized.toLowerCase(); }
    private static String safeFilename(String value) { if (value == null) return null; String safe = value.replaceAll("[^A-Za-z0-9._ -]", "_").trim(); return safe.substring(0, Math.min(255, safe.length())); }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    public record PendingIngestion(long resourceId, UUID ingestionId) {}
}

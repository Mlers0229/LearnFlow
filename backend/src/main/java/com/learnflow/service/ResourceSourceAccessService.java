package com.learnflow.service;

import com.learnflow.entity.ResourceBank;
import com.learnflow.entity.ResourceIngestion;
import com.learnflow.repository.ResourceBankRepository;
import com.learnflow.repository.ResourceIngestionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Service
public class ResourceSourceAccessService {

    public static final String VIEW_TEXT = "INLINE_TEXT";
    public static final String VIEW_PDF = "INLINE_PDF";
    public static final String VIEW_DOWNLOAD = "DOWNLOAD";

    private static final Set<String> DOWNLOAD_TYPES = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/rtf"
    );

    private final ResourceBankRepository resources;
    private final ResourceIngestionRepository ingestions;
    private final ResourceSourceStore sourceStore;

    public ResourceSourceAccessService(ResourceBankRepository resources,
                                       ResourceIngestionRepository ingestions,
                                       ResourceSourceStore sourceStore) {
        this.resources = resources;
        this.ingestions = ingestions;
        this.sourceStore = sourceStore;
    }

    @Transactional(readOnly = true)
    public SourceArtifact open(long resourceId, long requesterId, boolean admin) {
        ResourceBank resource = resources.findByIdAndStatusNot(resourceId, "DELETED")
                .orElseThrow(this::notFound);
        boolean owner = Long.valueOf(requesterId).equals(resource.getUploaderUserId());
        if (!admin && !owner && !"ACTIVE".equals(resource.getStatus())) {
            throw notFound();
        }
        if ("URL".equals(resource.getSourceType())) {
            throw new ResourceSourceAccessException(HttpStatus.CONFLICT, "RESOURCE_SOURCE_EXTERNAL",
                    "该资源使用外部链接，请打开原始链接查看");
        }
        if (!"SUCCEEDED".equals(resource.getIngestionStatus()) || resource.getCurrentIngestionId() == null) {
            throw new ResourceSourceAccessException(HttpStatus.CONFLICT, "RESOURCE_SOURCE_NOT_READY",
                    "资源处理成功后才能查看原件");
        }

        ResourceIngestion ingestion = ingestions.findById(resource.getCurrentIngestionId())
                .filter(item -> resource.getId().equals(item.getResourceId()))
                .filter(item -> "SUCCEEDED".equals(item.getStatus()))
                .filter(item -> item.getObjectKey() != null && !item.getObjectKey().isBlank())
                .orElseThrow(() -> new ResourceSourceAccessException(HttpStatus.SERVICE_UNAVAILABLE,
                        "RESOURCE_SOURCE_UNAVAILABLE", "资源原件暂时不可用"));

        SourcePresentation presentation = presentation(ingestion.getContentType());
        try {
            InputStream input = sourceStore.open(ingestion.getObjectKey());
            long contentLength = ingestion.getContentLength() == null ? -1L : ingestion.getContentLength();
            return new SourceArtifact(input, contentLength, presentation.contentType(),
                    safeFilename(ingestion.getOriginalFilename(), resource), presentation.viewMode(),
                    ingestion.getContentSha256(), resource.getSourceType());
        } catch (IOException failure) {
            throw new ResourceSourceAccessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RESOURCE_SOURCE_UNAVAILABLE", "资源原件暂时不可用");
        }
    }

    private SourcePresentation presentation(String rawContentType) {
        String type = rawContentType == null ? "" : rawContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("application/pdf".equals(type)) {
            return new SourcePresentation("application/pdf", VIEW_PDF);
        }
        if (DOWNLOAD_TYPES.contains(type)) {
            return new SourcePresentation(type, VIEW_DOWNLOAD);
        }
        if (type.startsWith("text/")) {
            return new SourcePresentation("text/plain;charset=UTF-8", VIEW_TEXT);
        }
        return new SourcePresentation("application/octet-stream", VIEW_DOWNLOAD);
    }

    private String safeFilename(String originalFilename, ResourceBank resource) {
        String fallback = "TEXT".equals(resource.getSourceType())
                ? "resource-" + resource.getId() + ".txt"
                : "resource-" + resource.getId();
        String value = originalFilename == null || originalFilename.isBlank() ? fallback : originalFilename.trim();
        value = value.replaceAll("[\\r\\n\\u0000/\\\\]", "_");
        if (value.isBlank()) value = fallback;
        return value.substring(0, Math.min(180, value.length()));
    }

    private ResourceSourceAccessException notFound() {
        return new ResourceSourceAccessException(HttpStatus.NOT_FOUND,
                "RESOURCE_SOURCE_NOT_FOUND", "资源不存在或无权查看");
    }

    private record SourcePresentation(String contentType, String viewMode) {}

    public record SourceArtifact(InputStream inputStream,
                                 long contentLength,
                                 String contentType,
                                 String filename,
                                 String viewMode,
                                 String contentSha256,
                                 String sourceType) {}
}

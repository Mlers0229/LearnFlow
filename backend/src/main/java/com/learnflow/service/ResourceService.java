package com.learnflow.service;

import com.learnflow.dto.ResourceCreateRequest;
import com.learnflow.dto.ResourceItemDto;
import com.learnflow.dto.ResourceUpdateRequest;
import com.learnflow.dto.ResourceQualityStatsDto;
import com.learnflow.dto.ResourceFeedbackDto;
import com.learnflow.dto.FeedbackTrendPoint;
import com.learnflow.entity.ResourceBank;
import com.learnflow.entity.UserResourceFeedback;
import com.learnflow.repository.ResourceBankRepository;
import com.learnflow.repository.UserResourceFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.net.URI;
import java.net.URISyntaxException;

@Service
public class ResourceService {

    private final ResourceBankRepository resourceBankRepository;

    private final UserResourceFeedbackRepository userResourceFeedbackRepository;

    private final AdminAuditLogService auditLogService;

    public ResourceService(ResourceBankRepository resourceBankRepository,
                           UserResourceFeedbackRepository userResourceFeedbackRepository,
                           AdminAuditLogService auditLogService) {
        this.resourceBankRepository = resourceBankRepository;
        this.userResourceFeedbackRepository = userResourceFeedbackRepository;
        this.auditLogService = auditLogService;
    }

    public ResourceItemDto createResource(ResourceCreateRequest request) {
        validatePublicResourceUrl(request.getUrl());
        // 姒涙顓婚幐澶屽弾閳ユ粎鏁ら幋閿嬪絹娴溿倛绁┃鎰ㄢ偓婵嗩槱閻炲棴绱濋崚婵嗩潗閻樿埖鈧椒璐?PENDING閿涘苯绶熺粻锛勬倞缁旑垰顓搁弽?
        ResourceBank entity = new ResourceBank();
        entity.setUploaderUserId(request.getUploaderUserId());
        entity.setUploaderUsername(request.getUploaderUsername());
        entity.setTitle(request.getTitle());
        entity.setUrl(request.getUrl());
        entity.setLevel(request.getLevel());
        entity.setDomain(normalizeDomain(request.getDomain()));
        entity.setDurationMinutes(request.getDurationMinutes());
        entity.setTags(request.getTags());
        entity.setStatus("PENDING");

        ResourceBank saved = resourceBankRepository.save(entity);
        return toDto(saved);
    }

    public List<ResourceItemDto> listMyResources(Long uploaderUserId, String uploaderUsername) {
        List<ResourceBank> list;
        if (uploaderUserId != null) {
            list = resourceBankRepository.findByUploaderUserIdAndStatusNotOrderByCreatedAtDesc(uploaderUserId, "DELETED");
            if ((list == null || list.isEmpty()) && uploaderUsername != null && !uploaderUsername.isBlank()) {
                list = resourceBankRepository.findByUploaderUsernameAndStatusNotOrderByCreatedAtDesc(uploaderUsername.trim(), "DELETED");
            }
        } else if (uploaderUsername != null && !uploaderUsername.isBlank()) {
            list = resourceBankRepository.findByUploaderUsernameAndStatusNotOrderByCreatedAtDesc(uploaderUsername.trim(), "DELETED");
        } else {
            throw new IllegalArgumentException("\u7f3a\u5c11\u4e0a\u4f20\u4eba\u6807\u8bc6");
        }
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ResourceItemDto> listActiveResources() {
        List<ResourceBank> list = resourceBankRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * 缁狅紕鎮婄粩顖涚叀閻澧嶉張澶庣カ濠ф劧绱欓崠鍛儓 PENDING / ACTIVE / INACTIVE閿涘鈧?
     */
    public List<ResourceItemDto> listAllResources() {
        List<ResourceBank> list = resourceBankRepository.findAllByStatusNotOrderByCreatedAtDesc("DELETED");
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void deleteResource(Long id, Long requesterUserId, boolean admin) {
        ResourceBank resource = resourceBankRepository.findByIdAndStatusNot(id, "DELETED")
                .orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        if (!admin && !requesterUserId.equals(resource.getUploaderUserId())) {
            throw new IllegalArgumentException("资源不存在");
        }
        if (!admin && "ACTIVE".equals(resource.getStatus())) {
            throw new ResourceDeletionException("ACTIVE_RESOURCE_DELETE_FORBIDDEN", "已上线资源请联系管理员下线后删除");
        }
        if ("PENDING".equals(resource.getIngestionStatus()) || "PROCESSING".equals(resource.getIngestionStatus())) {
            throw new ResourceDeletionException("RESOURCE_INGESTION_IN_PROGRESS", "资源正在处理中，请等待处理结束后删除");
        }
        resource.setStatus("DELETED");
        resourceBankRepository.save(resource);
        auditLogService.record("RESOURCE_DELETE", admin ? "admin" : "user", "RESOURCE", id, "soft_deleted");
    }

    /**
     * 鐠侊紕鐣诲В蹇旀蒋鐠у嫭绨惃鍕暆閸楁洝宸濋柌蹇曠埠鐠佲€蹭繆閹垬鈧?
     *
     * 鏉╂柨娲栭惃鍕灙鐞涖劋鑵戦崣顏勫瘶閸氼偀鈧粏鍤︾亸鎴炴箒娑撯偓閺夆€冲冀妫ｅ牃鈧繄娈戠挧鍕爱閿?
     * 楠炲啿娼庨崚鍡楃摟濞?avgRating 瀹稿弶妲?Double閿涘苯褰查崷銊ュ缁旑垯绻氶悾?1 娴ｅ秵鍨?2 娴ｅ秴鐨弫鏉跨潔缁€鎭掆偓?
     */
    public List<ResourceQualityStatsDto> aggregateQualityStats() {
        List<Object[]> rows = userResourceFeedbackRepository.aggregateByResource();
        return rows.stream().map(row -> {
            ResourceQualityStatsDto dto = new ResourceQualityStatsDto();
            dto.setResourceId((Long) row[0]);
            // avgRating 閸欘垵鍏樻稉?null
            dto.setAvgRating(row[1] instanceof Number n ? n.doubleValue() : null);
            dto.setFeedbackCount(row[2] instanceof Number n2 ? n2.longValue() : 0L);
            dto.setInvalidReportCount(row[3] instanceof Number n3 ? n3.longValue() : 0L);
            return dto;
        }).collect(Collectors.toList());
    }

    public List<FeedbackTrendPoint> dailyTrend(int days) {
        List<Object[]> rows = userResourceFeedbackRepository.aggregateDaily(days);
        return rows.stream().map(row -> {
            FeedbackTrendPoint p = new FeedbackTrendPoint();
            p.setDay(row[0] instanceof java.sql.Date d ? d.toLocalDate() : null);
            p.setAvgRating(row[1] instanceof Number n ? n.doubleValue() : null);
            p.setFeedbackCount(row[2] instanceof Number n2 ? n2.longValue() : 0L);
            p.setInvalidReportCount(row[3] instanceof Number n3 ? n3.longValue() : 0L);
            return p;
        }).toList();
    }

    public List<ResourceItemDto> enrichRecommendedResources(List<ResourceItemDto> items, Long userId) {
        if (items == null || items.isEmpty()) {
            return items;
        }

        List<Long> resourceIds = items.stream()
                .map(ResourceItemDto::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (resourceIds.isEmpty()) {
            return items;
        }

        Map<Long, Object[]> aggregateMap = new HashMap<>();
        for (Object[] row : userResourceFeedbackRepository.aggregateByResourceIds(resourceIds)) {
            if (row[0] instanceof Long resourceId) {
                aggregateMap.put(resourceId, row);
            }
        }

        Map<Long, UserResourceFeedback> latestUserFeedbackMap = new HashMap<>();
        if (userId != null) {
            for (UserResourceFeedback feedback : userResourceFeedbackRepository
                    .findByUser_IdAndResource_IdInOrderByCreatedAtDesc(userId, resourceIds)) {
                Long resourceId = feedback.getResource() != null ? feedback.getResource().getId() : null;
                if (resourceId != null && !latestUserFeedbackMap.containsKey(resourceId)) {
                    latestUserFeedbackMap.put(resourceId, feedback);
                }
            }
        }

        for (ResourceItemDto item : items) {
            if (item.getId() == null) {
                continue;
            }
            Object[] aggregate = aggregateMap.get(item.getId());
            if (aggregate != null) {
                item.setAvgRating(aggregate[1] instanceof Number n ? n.doubleValue() : null);
                item.setFeedbackCount(aggregate[2] instanceof Number n2 ? n2.longValue() : 0L);
                item.setInvalidReportCount(aggregate[3] instanceof Number n3 ? n3.longValue() : 0L);
            } else {
                item.setFeedbackCount(0L);
                item.setInvalidReportCount(0L);
            }

            UserResourceFeedback latest = latestUserFeedbackMap.get(item.getId());
            if (latest != null) {
                item.setCurrentUserRating(latest.getRating());
                item.setCurrentUserReportedInvalid(Boolean.TRUE.equals(latest.getReportedInvalid()));
                if (Boolean.TRUE.equals(latest.getReportedInvalid())) {
                    item.setCurrentUserFeedback("invalid");
                } else if (latest.getRating() != null) {
                    item.setCurrentUserFeedback("helpful");
                }
            }
        }
        return items;
    }

    public List<ResourceItemDto> prepareRecommendedResources(List<ResourceItemDto> items, Long userId, int limit) {
        List<ResourceItemDto> enriched = enrichRecommendedResources(items, userId);
        if (enriched == null || enriched.isEmpty()) {
            return enriched;
        }

        List<ResourceItemDto> filtered = enriched.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getCurrentUserReportedInvalid()))
                .collect(Collectors.toCollection(ArrayList::new));
        List<ResourceItemDto> pool = filtered.isEmpty() ? new ArrayList<>(enriched) : filtered;

        pool.sort(
                Comparator.comparingDouble(this::calculateRecommendationCompositeScore).reversed()
                        .thenComparing(Comparator.comparingDouble((ResourceItemDto item) -> safeDouble(item.getScore())).reversed())
                        .thenComparing(Comparator.comparingLong((ResourceItemDto item) -> safeLong(item.getFeedbackCount())).reversed())
        );

        int realLimit = limit > 0 ? Math.min(limit, pool.size()) : pool.size();
        return new ArrayList<>(pool.subList(0, realLimit));
    }

    public List<ResourceFeedbackDto> recentFeedbacks(Long resourceId, int limit) {
        List<Object[]> rows = userResourceFeedbackRepository.findRecentByResource(resourceId, limit);
        return rows.stream().map(row -> {
            ResourceFeedbackDto dto = new ResourceFeedbackDto();
            dto.setId(row[0] instanceof Number n ? n.longValue() : null);
            dto.setRating(row[1] instanceof Number n1 ? n1.intValue() : null);
            dto.setComment(row[2] != null ? row[2].toString() : null);
            dto.setReportedInvalid(row[3] instanceof Boolean b ? b : null);
            dto.setCreatedAt(row[4] instanceof java.sql.Timestamp t ? t.toLocalDateTime() : null);
            dto.setUserId(row[5] instanceof Number n5 ? n5.longValue() : null);
            return dto;
        }).toList();
    }

    /**
     * 缁狅紕鎮婄粩顖涙纯閺傛媽绁┃鎰Ц閹緤绱癙ENDING / ACTIVE / INACTIVE閵?
     */
    public void updateStatus(Long id, String status) {
        ResourceBank entity = resourceBankRepository.findByIdAndStatusNot(id, "DELETED")
                .orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        validateActivationReadiness(entity, status);
        entity.setStatus(status);
        resourceBankRepository.save(entity);
        auditLogService.record("RESOURCE_STATUS", "admin", "RESOURCE", id, "status=" + status);
    }

    public void batchUpdateStatus(List<Long> ids, String status) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<ResourceBank> list = resourceBankRepository.findAllById(ids);
        for (ResourceBank entity : list) {
            validateActivationReadiness(entity, status);
            entity.setStatus(status);
        }
        resourceBankRepository.saveAll(list);
        auditLogService.record("RESOURCE_STATUS_BATCH", "admin", "RESOURCE", null,
                "ids=" + ids + ",status=" + status);
    }

    public void updateResourceInfo(Long id, ResourceUpdateRequest request) {
        ResourceBank entity = resourceBankRepository.findByIdAndStatusNot(id, "DELETED")
                .orElseThrow(() -> new IllegalArgumentException("资源不存在"));
        if (request.getTitle() != null) {
            entity.setTitle(request.getTitle());
        }
        if (request.getUrl() != null) {
            validatePublicResourceUrl(request.getUrl());
            entity.setUrl(request.getUrl());
        }
        if (request.getLevel() != null) {
            entity.setLevel(request.getLevel());
        }
        if (request.getDomain() != null) {
            entity.setDomain(normalizeDomain(request.getDomain()));
        }
        if (request.getDurationMinutes() != null) {
            entity.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getTags() != null) {
            entity.setTags(request.getTags());
        }
        resourceBankRepository.save(entity);
        auditLogService.record("RESOURCE_EDIT", "admin", "RESOURCE", id, "edited");
    }

    private double calculateRecommendationCompositeScore(ResourceItemDto item) {
        double score = safeDouble(item.getScore());
        double avgRating = safeDouble(item.getAvgRating());
        long feedbackCount = safeLong(item.getFeedbackCount());
        long invalidReportCount = safeLong(item.getInvalidReportCount());

        if (avgRating > 0) {
            score += Math.max(0.0, avgRating - 3.0) * 0.45;
        }
        if (feedbackCount > 0) {
            score += Math.min(0.8, Math.log(feedbackCount + 1) * 0.25);
        }
        if (invalidReportCount > 0) {
            score -= Math.min(1.2, invalidReportCount * 0.35);
        }
        if (feedbackCount >= 3 && invalidReportCount > 0) {
            score -= Math.min(1.4, invalidReportCount * 1.0 / feedbackCount * 1.6);
        }
        if (item.getCurrentUserRating() != null) {
            score += (item.getCurrentUserRating() - 3) * 0.3;
        }
        if ("helpful".equalsIgnoreCase(item.getCurrentUserFeedback())) {
            score += 0.9;
        }
        if (Boolean.TRUE.equals(item.getCurrentUserReportedInvalid())
                || "invalid".equalsIgnoreCase(item.getCurrentUserFeedback())) {
            score -= 3.0;
        }

        return score;
    }

    private double safeDouble(Number value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private long safeLong(Number value) {
        return value != null ? value.longValue() : 0L;
    }

    private ResourceItemDto toDto(ResourceBank entity) {
        ResourceItemDto dto = new ResourceItemDto();
        dto.setId(entity.getId());
        dto.setUploaderUserId(entity.getUploaderUserId());
        dto.setUploaderUsername(entity.getUploaderUsername());
        dto.setTitle(entity.getTitle());
        dto.setUrl(entity.getUrl());
        dto.setSourceType(entity.getSourceType());
        dto.setIngestionStatus(entity.getIngestionStatus());
        dto.setCurrentIngestionId(entity.getCurrentIngestionId());
        dto.setLevel(entity.getLevel());
        dto.setDomain(entity.getDomain());
        dto.setDurationMinutes(entity.getDurationMinutes());
        dto.setTags(entity.getTags());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setReason(null);
        return dto;
    }

    private String normalizeDomain(String domain) {
        if (domain == null) {
            return null;
        }
        String normalized = domain.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validatePublicResourceUrl(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            if ((scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("资源 URL 必须是无内嵌凭证的 HTTP(S) 地址");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("资源 URL 格式不正确", exception);
        }
    }

    private void validateActivationReadiness(ResourceBank resource, String status) {
        if (!"ACTIVE".equals(status)) return;
        String ingestionStatus = resource.getIngestionStatus();
        if (ingestionStatus != null && !"NOT_STARTED".equals(ingestionStatus) && !"SUCCEEDED".equals(ingestionStatus)) {
            throw new ResourceActivationException(
                    "RESOURCE_INGESTION_NOT_READY",
                    "资源摄取成功后才能上线；请先重新摄取或更换可访问的来源"
            );
        }
    }
}





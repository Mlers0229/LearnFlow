package com.learnflow.controller;

import com.learnflow.dto.ResourceCreateRequest;
import com.learnflow.dto.ResourceUpdateRequest;
import com.learnflow.dto.ResourceBatchStatusRequest;
import com.learnflow.dto.ResourceFeedbackRequest;
import com.learnflow.dto.ResourceItemDto;
import com.learnflow.dto.ResourceQualityStatsDto;
import com.learnflow.dto.ResourceFeedbackDto;
import com.learnflow.dto.FeedbackTrendPoint;
import com.learnflow.service.ResourceFeedbackService;
import com.learnflow.service.ResourceActivationException;
import com.learnflow.service.ResourceDeletionException;
import com.learnflow.service.ResourceService;
import com.learnflow.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    private final ResourceFeedbackService resourceFeedbackService;
    private final CurrentUserService currentUserService;

    public ResourceController(ResourceService resourceService,
                              ResourceFeedbackService resourceFeedbackService,
                              CurrentUserService currentUserService) {
        this.resourceService = resourceService;
        this.resourceFeedbackService = resourceFeedbackService;
        this.currentUserService = currentUserService;
    }

    /**
     * 用户上传新的学习资源。
     * 初始状态为 PENDING，待管理端审核后才会变为 ACTIVE 并参与推荐。
     */
    @PostMapping
    public ResponseEntity<ResourceItemDto> create(@Valid @RequestBody ResourceCreateRequest request) {
        request.setUploaderUserId(currentUserService.requireUserId());
        request.setUploaderUsername(currentUserService.requireUsername());
        ResourceItemDto dto = resourceService.createResource(request);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    /**
     * 管理端：获取所有学习资源列表（包含 PENDING / ACTIVE / INACTIVE）。
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ResourceItemDto>> list() {
        List<ResourceItemDto> list = resourceService.listAllResources();
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ResourceItemDto>> listMine() {
        try {
            List<ResourceItemDto> list = resourceService.listMyResources(
                    currentUserService.requireUserId(), currentUserService.requireUsername()
            );
            return new ResponseEntity<>(list, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResource(@PathVariable("id") Long id) {
        try {
            resourceService.deleteResource(id, currentUserService.requireUserId(), currentUserService.isAdmin());
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (ResourceDeletionException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", exception.getCode(),
                    "message", exception.getMessage()
            ));
        } catch (IllegalArgumentException exception) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 管理端：批量更新资源状态。
     */
    @PostMapping("/batch/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> batchStatus(@RequestBody ResourceBatchStatusRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
        if (!status.equals("PENDING") && !status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            resourceService.batchUpdateStatus(request.getIds(), status);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (ResourceActivationException exception) {
            return activationConflict(exception);
        }
    }

    /**
     * 管理端：编辑资源基础信息。
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateResource(@PathVariable("id") Long id,
                                               @RequestBody ResourceUpdateRequest request) {
        try {
            resourceService.updateResourceInfo(id, request);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 管理端：查看某条资源的最近反馈明细。
     */
    @GetMapping("/{id}/feedbacks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ResourceFeedbackDto>> feedbacks(@PathVariable("id") Long id,
                                                               @RequestParam(value = "limit", required = false) Integer limit) {
        int realLimit = (limit == null || limit <= 0) ? 20 : limit;
        List<ResourceFeedbackDto> list = resourceService.recentFeedbacks(id, realLimit);
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    /**
     * 管理端：按天聚合的评分 / 反馈 / 举报趋势。
     */
    @GetMapping("/feedback/trend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FeedbackTrendPoint>> feedbackTrend(@RequestParam(value = "days", required = false) Integer days) {
        int realDays = (days == null || days <= 0) ? 30 : days;
        List<FeedbackTrendPoint> list = resourceService.dailyTrend(realDays);
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    /**
     * 管理端：获取按资源聚合的质量统计信息（平均评分 / 反馈数 / 举报数）。
     *
     * 典型用途：在管理端资源质量看板中展示每条资源的大致“受欢迎程度”和“问题程度”。
     */
    @GetMapping("/quality-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ResourceQualityStatsDto>> qualityStats() {
        List<ResourceQualityStatsDto> stats = resourceService.aggregateQualityStats();
        return new ResponseEntity<>(stats, HttpStatus.OK);
    }

    /**
     * 管理端：更新资源状态（审核通过 / 下线等）。
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable("id") Long id,
                                          @RequestParam("status") String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals("PENDING") && !normalized.equals("ACTIVE") && !normalized.equals("INACTIVE")) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            resourceService.updateStatus(id, normalized);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (ResourceActivationException exception) {
            return activationConflict(exception);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    private ResponseEntity<Map<String, String>> activationConflict(ResourceActivationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage()
        ));
    }

    /**
     * 用户对某条资源提交评分 / 举报反馈。
     *
     * 演示环境不做鉴权，只要传入 resourceId 和简单的反馈信息即可。
     */
    @PostMapping("/{id}/feedback")
    public ResponseEntity<Void> createFeedback(@PathVariable("id") Long id,
                                               @Valid @RequestBody ResourceFeedbackRequest request) {
        try {
            resourceFeedbackService.createFeedback(id, currentUserService.requireUserId(), request);
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}



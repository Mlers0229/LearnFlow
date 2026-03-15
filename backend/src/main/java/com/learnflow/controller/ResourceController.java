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
import com.learnflow.service.ResourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
@CrossOrigin
public class ResourceController {

    private final ResourceService resourceService;

    private final ResourceFeedbackService resourceFeedbackService;

    public ResourceController(ResourceService resourceService,
                              ResourceFeedbackService resourceFeedbackService) {
        this.resourceService = resourceService;
        this.resourceFeedbackService = resourceFeedbackService;
    }

    /**
     * 用户上传新的学习资源。
     * 初始状态为 PENDING，待管理端审核后才会变为 ACTIVE 并参与推荐。
     */
    @PostMapping
    public ResponseEntity<ResourceItemDto> create(@Valid @RequestBody ResourceCreateRequest request) {
        ResourceItemDto dto = resourceService.createResource(request);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    /**
     * 管理端：获取所有学习资源列表（包含 PENDING / ACTIVE / INACTIVE）。
     */
    @GetMapping
    public ResponseEntity<List<ResourceItemDto>> list() {
        List<ResourceItemDto> list = resourceService.listAllResources();
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ResourceItemDto>> listMine(@RequestParam(value = "userId", required = false) Long userId,
                                                          @RequestParam(value = "username", required = false) String username) {
        try {
            List<ResourceItemDto> list = resourceService.listMyResources(userId, username);
            return new ResponseEntity<>(list, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 管理端：批量更新资源状态。
     */
    @PostMapping("/batch/status")
    public ResponseEntity<Void> batchStatus(@RequestBody ResourceBatchStatusRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
        if (!status.equals("PENDING") && !status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        resourceService.batchUpdateStatus(request.getIds(), status);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * 管理端：编辑资源基础信息。
     */
    @PatchMapping("/{id}")
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
    public ResponseEntity<List<ResourceQualityStatsDto>> qualityStats() {
        List<ResourceQualityStatsDto> stats = resourceService.aggregateQualityStats();
        return new ResponseEntity<>(stats, HttpStatus.OK);
    }

    /**
     * 管理端：更新资源状态（审核通过 / 下线等）。
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable("id") Long id,
                                             @RequestParam("status") String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals("PENDING") && !normalized.equals("ACTIVE") && !normalized.equals("INACTIVE")) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            resourceService.updateStatus(id, normalized);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
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
            resourceFeedbackService.createFeedback(id, request);
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}



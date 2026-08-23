package com.learnflow.service;

import com.learnflow.dto.ResourceFeedbackRequest;
import com.learnflow.entity.ResourceBank;
import com.learnflow.entity.User;
import com.learnflow.entity.UserResourceFeedback;
import com.learnflow.repository.ResourceBankRepository;
import com.learnflow.repository.UserRepository;
import com.learnflow.repository.UserResourceFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 用户资源反馈相关逻辑。
 *
 * 当前版本会将同一用户对同一资源的反馈更新到最近一条记录上，
 * 避免前端重复点击后产生大量重复反馈数据。
 */
@Service
public class ResourceFeedbackService {

    private final ResourceBankRepository resourceBankRepository;
    private final UserRepository userRepository;
    private final UserResourceFeedbackRepository userResourceFeedbackRepository;
    private final MasteryService masteryService;

    public ResourceFeedbackService(ResourceBankRepository resourceBankRepository,
                                   UserRepository userRepository,
                                   UserResourceFeedbackRepository userResourceFeedbackRepository,
                                   MasteryService masteryService) {
        this.resourceBankRepository = resourceBankRepository;
        this.userRepository = userRepository;
        this.userResourceFeedbackRepository = userResourceFeedbackRepository;
        this.masteryService = masteryService;
    }

    /**
     * 为指定资源创建或更新一条用户反馈。
     *
     * @param resourceId 资源 ID（resource_bank.id）
     * @param request    请求体，包含 rating / comment / reportedInvalid
     * @throws IllegalArgumentException 当资源不存在时抛出
     */
    @Transactional
    public void createFeedback(Long resourceId, Long userId, ResourceFeedbackRequest request) {
        ResourceBank resource = resourceBankRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在，id=" + resourceId));

        UserResourceFeedback feedback = userResourceFeedbackRepository
                .findTopByUser_IdAndResource_IdOrderByCreatedAtDesc(userId, resourceId)
                .orElseGet(UserResourceFeedback::new);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        feedback.setUser(user);

        feedback.setResource(resource);
        feedback.setRating(request.getRating());
        feedback.setComment(request.getComment());
        feedback.setReportedInvalid(request.getReportedInvalid());

        UserResourceFeedback saved = userResourceFeedbackRepository.save(feedback);
        masteryService.recordResourceFeedback(userId, saved);
    }
}

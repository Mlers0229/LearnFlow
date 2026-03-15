package com.learnflow.service;

import com.learnflow.dto.ResourceFeedbackRequest;
import com.learnflow.entity.ResourceBank;
import com.learnflow.entity.User;
import com.learnflow.entity.UserResourceFeedback;
import com.learnflow.repository.ResourceBankRepository;
import com.learnflow.repository.UserRepository;
import com.learnflow.repository.UserResourceFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户资源反馈相关逻辑。
 *
 * 当前版本会将同一用户对同一资源的反馈更新到最近一条记录上，
 * 避免前端重复点击后产生大量重复反馈数据。
 */
@Service
public class ResourceFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ResourceFeedbackService.class);

    private final ResourceBankRepository resourceBankRepository;
    private final UserRepository userRepository;
    private final UserResourceFeedbackRepository userResourceFeedbackRepository;

    public ResourceFeedbackService(ResourceBankRepository resourceBankRepository,
                                   UserRepository userRepository,
                                   UserResourceFeedbackRepository userResourceFeedbackRepository) {
        this.resourceBankRepository = resourceBankRepository;
        this.userRepository = userRepository;
        this.userResourceFeedbackRepository = userResourceFeedbackRepository;
    }

    /**
     * 为指定资源创建或更新一条用户反馈。
     *
     * @param resourceId 资源 ID（resource_bank.id）
     * @param request    请求体，包含 rating / comment / reportedInvalid
     * @throws IllegalArgumentException 当资源不存在时抛出
     */
    public void createFeedback(Long resourceId, ResourceFeedbackRequest request) {
        ResourceBank resource = resourceBankRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在，id=" + resourceId));

        UserResourceFeedback feedback;
        if (request.getUserId() != null) {
            feedback = userResourceFeedbackRepository
                    .findTopByUser_IdAndResource_IdOrderByCreatedAtDesc(request.getUserId(), resourceId)
                    .orElseGet(UserResourceFeedback::new);

            Optional<User> userOpt = userRepository.findById(request.getUserId());
            userOpt.ifPresent(feedback::setUser);
        } else {
            feedback = new UserResourceFeedback();
        }

        feedback.setResource(resource);
        feedback.setRating(request.getRating());
        feedback.setComment(request.getComment());
        feedback.setReportedInvalid(request.getReportedInvalid());

        try {
            userResourceFeedbackRepository.save(feedback);
        } catch (Exception e) {
            // 不因反馈写入失败而影响主流程（例如前端仍可继续浏览资源）
            log.error("保存用户资源反馈失败，但不会中断接口调用。resourceId={}", resourceId, e);
        }
    }
}

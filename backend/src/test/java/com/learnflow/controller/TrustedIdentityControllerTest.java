package com.learnflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.dto.GoalRequest;
import com.learnflow.dto.PlanResponse;
import com.learnflow.dto.ResourceCreateRequest;
import com.learnflow.dto.ResourceFeedbackRequest;
import com.learnflow.dto.ResourceItemDto;
import com.learnflow.repository.StudyPlanDayRepository;
import com.learnflow.service.AdaptiveLearningService;
import com.learnflow.service.AiProxyService;
import com.learnflow.service.ChatProxyService;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.ExerciseRecordService;
import com.learnflow.service.LearningProgressService;
import com.learnflow.service.PlanQueryService;
import com.learnflow.service.PlanReplanService;
import com.learnflow.service.ResourceFeedbackService;
import com.learnflow.service.ResourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrustedIdentityControllerTest {

    private final CurrentUserService currentUserService = new CurrentUserService();

    @BeforeEach
    void authenticateUser() {
        Jwt jwt = new Jwt(
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                java.util.Map.of("alg", "none"),
                java.util.Map.of("sub", "7", "username", "alice")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void planGenerationOverwritesClientSuppliedUserId() {
        AiProxyService aiProxyService = mock(AiProxyService.class);
        when(aiProxyService.generatePlan(any())).thenReturn(new PlanResponse());
        PlanController controller = planController(aiProxyService);
        GoalRequest request = new GoalRequest();
        request.setUserId(999L);

        controller.generatePlan(request);

        ArgumentCaptor<GoalRequest> captor = ArgumentCaptor.forClass(GoalRequest.class);
        verify(aiProxyService).generatePlan(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    }

    @Test
    void resourceUploadOverwritesClientSuppliedIdentity() {
        ResourceService resourceService = mock(ResourceService.class);
        when(resourceService.createResource(any())).thenReturn(new ResourceItemDto());
        ResourceController controller = new ResourceController(
                resourceService,
                mock(ResourceFeedbackService.class),
                currentUserService
        );
        ResourceCreateRequest request = new ResourceCreateRequest();
        request.setUploaderUserId(999L);
        request.setUploaderUsername("mallory");

        controller.create(request);

        ArgumentCaptor<ResourceCreateRequest> captor = ArgumentCaptor.forClass(ResourceCreateRequest.class);
        verify(resourceService).createResource(captor.capture());
        assertThat(captor.getValue().getUploaderUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getUploaderUsername()).isEqualTo("alice");
    }

    @Test
    void resourceFeedbackIgnoresClientSuppliedUserId() {
        ResourceFeedbackService feedbackService = mock(ResourceFeedbackService.class);
        ResourceController controller = new ResourceController(
                mock(ResourceService.class),
                feedbackService,
                currentUserService
        );
        ResourceFeedbackRequest request = new ResourceFeedbackRequest();
        request.setUserId(999L);

        controller.createFeedback(12L, request);

        verify(feedbackService).createFeedback(12L, 7L, request);
    }

    @Test
    void exerciseQueriesAlwaysUseAuthenticatedUserId() {
        ExerciseRecordService exerciseService = mock(ExerciseRecordService.class);
        ExerciseRecordController controller = new ExerciseRecordController(exerciseService, currentUserService);

        controller.listRecords(33L, 44L, 25);

        verify(exerciseService).listRecords(7L, 33L, 44L, 25);
    }

    private PlanController planController(AiProxyService aiProxyService) {
        return new PlanController(
                aiProxyService,
                mock(PlanQueryService.class),
                mock(StudyPlanDayRepository.class),
                mock(ExerciseRecordService.class),
                mock(ResourceService.class),
                mock(PlanReplanService.class),
                new ObjectMapper(),
                currentUserService,
                mock(LearningProgressService.class),
                mock(AdaptiveLearningService.class)
        );
    }
}

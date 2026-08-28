package com.learnflow.controller;

import com.learnflow.config.JwtConfig;
import com.learnflow.config.SecurityConfig;
import com.learnflow.dto.AdminDashboardSummaryDto;
import com.learnflow.service.AdminAuditLogService;
import com.learnflow.service.AdminDashboardService;
import com.learnflow.service.AiProxyService;
import com.learnflow.service.AsyncTaskService;
import com.learnflow.service.ChatProxyService;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.ResourceFeedbackService;
import com.learnflow.service.ResourceActivationException;
import com.learnflow.service.ResourceDeletionException;
import com.learnflow.service.ResourceSourceAccessService;
import com.learnflow.service.ResourceService;
import com.learnflow.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AdminDashboardController.class,
        AdminAuditController.class,
        AdminUserController.class,
        DebugController.class,
        ChatProxyController.class,
        ResourceController.class,
        AdminAsyncTaskController.class
})
@Import({SecurityConfig.class, JwtConfig.class, CurrentUserService.class})
@TestPropertySource(properties = {
        "learnflow.auth.jwt-secret=0123456789abcdef0123456789abcdef",
        "learnflow.cors.allowed-origins=https://learnflow.example.com"
})
class AdminAuthorizationWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminDashboardService adminDashboardService;
    @MockBean
    private AdminAuditLogService adminAuditLogService;
    @MockBean
    private UserAdminService userAdminService;
    @MockBean
    private AiProxyService aiProxyService;
    @MockBean
    private ChatProxyService chatProxyService;
    @MockBean
    private ResourceService resourceService;
    @MockBean
    private ResourceSourceAccessService resourceSourceAccessService;
    @MockBean
    private ResourceFeedbackService resourceFeedbackService;
    @MockBean
    private AsyncTaskService asyncTaskService;
    static Stream<MockHttpServletRequestBuilder> adminReadEndpoints() {
        return Stream.of(
                get("/api/admin/dashboard"),
                get("/api/admin/audit/logs"),
                get("/api/admin/users"),
                get("/api/agent/logs"),
                get("/api/chat/admin-config"),
                get("/api/admin/tasks/failed"),
                get("/api/resources")
        );
    }

    @Test
    void anonymousRequestCannotAccessProtectedApi() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("adminReadEndpoints")
    void studentCannotAccessAnyAdminReadSurface(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdminDashboard() throws Exception {
        when(adminDashboardService.getDashboardSummary(120, 50, 7))
                .thenReturn(new AdminDashboardSummaryDto());

        mockMvc.perform(get("/api/admin/dashboard")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void resourceOwnerReceivesSafeViewerHeadersAndSourceBody() throws Exception {
        byte[] source = "safe notes".getBytes(StandardCharsets.UTF_8);
        when(resourceSourceAccessService.open(1L, 7L, false)).thenReturn(
                new ResourceSourceAccessService.SourceArtifact(
                        new ByteArrayInputStream(source), source.length, "text/plain;charset=UTF-8",
                        "notes.txt", ResourceSourceAccessService.VIEW_TEXT, "a".repeat(64), "TEXT"
                )
        );

        mockMvc.perform(get("/api/resources/1/source")
                        .with(jwt().jwt(token -> token.subject("7").claim("username", "student"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(source))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Resource-View-Mode", "INLINE_TEXT"))
                .andExpect(header().string("X-Resource-Source-Type", "TEXT"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void resourceDeletionConflictHasAStableErrorCode() throws Exception {
        doThrow(new ResourceDeletionException("RESOURCE_INGESTION_IN_PROGRESS", "资源正在处理中，请等待处理结束后删除"))
                .when(resourceService).deleteResource(1L, 7L, false);

        mockMvc.perform(delete("/api/resources/1")
                        .with(jwt().jwt(token -> token.subject("7").claim("username", "student"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_INGESTION_IN_PROGRESS"));
    }

    @Test
    void activationConflictIsNotReportedAsMissingResource() throws Exception {
        doThrow(new ResourceActivationException(
                "RESOURCE_INGESTION_NOT_READY",
                "资源摄取成功后才能上线；请先重新摄取或更换可访问的来源"
        )).when(resourceService).updateStatus(1L, "ACTIVE");

        mockMvc.perform(patch("/api/resources/1/status")
                        .param("status", "ACTIVE")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_INGESTION_NOT_READY"))
                .andExpect(jsonPath("$.message").value("资源摄取成功后才能上线；请先重新摄取或更换可访问的来源"));
    }
}

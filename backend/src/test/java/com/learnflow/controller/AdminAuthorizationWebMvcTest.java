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

import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

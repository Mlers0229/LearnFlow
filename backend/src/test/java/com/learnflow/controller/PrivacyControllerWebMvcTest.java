package com.learnflow.controller;

import com.learnflow.config.JwtConfig;
import com.learnflow.config.SecurityConfig;
import com.learnflow.dto.PrivacyRequestResponse;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.PrivacyRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrivacyController.class)
@Import({SecurityConfig.class, JwtConfig.class, CurrentUserService.class})
@TestPropertySource(properties = {
        "learnflow.auth.jwt-secret=0123456789abcdef0123456789abcdef",
        "learnflow.cors.allowed-origins=https://learnflow.example.com"
})
class PrivacyControllerWebMvcTest {
    @Autowired private MockMvc mockMvc;

    @MockBean private PrivacyRequestService privacy;

    @Test
    void anonymousCannotCreateDataExport() throws Exception {
        mockMvc.perform(post("/api/privacy/exports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedExportUsesJwtSubjectOnly() throws Exception {
        UUID id = UUID.randomUUID();
        when(privacy.requestExport(42L, "export:request-1234"))
                .thenReturn(response(id, "EXPORT"));

        mockMvc.perform(post("/api/privacy/exports")
                        .header("Idempotency-Key", "export:request-1234")
                        .with(jwt().jwt(token -> token.subject("42"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value("EXPORT"));

        verify(privacy).requestExport(42L, "export:request-1234");
    }

    @Test
    void erasureDoesNotAcceptClientSuppliedUserIdentity() throws Exception {
        UUID id = UUID.randomUUID();
        when(privacy.requestErasure(eq(42L), eq("erasure:request-1234"), any()))
                .thenReturn(response(id, "ERASURE"));

        mockMvc.perform(post("/api/privacy/erasure")
                        .header("Idempotency-Key", "erasure:request-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret\",\"confirmation\":\"DELETE alice\",\"userId\":999}")
                        .with(jwt().jwt(token -> token.subject("42"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("ERASURE"));

        verify(privacy).requestErasure(eq(42L), eq("erasure:request-1234"), any());
    }

    private PrivacyRequestResponse response(UUID id, String type) {
        return new PrivacyRequestResponse(id, type, "PENDING", false, null, null,
                OffsetDateTime.now(ZoneOffset.UTC), null);
    }
}

package com.learnflow.controller;

import com.learnflow.config.JwtConfig;
import com.learnflow.config.SecurityConfig;
import com.learnflow.service.AuthService;
import com.learnflow.service.AuthSessionService;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.LoginAttemptService;
import com.learnflow.service.PasswordResetService;
import com.learnflow.service.PasswordResetAttemptService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "learnflow.auth.jwt-secret=0123456789abcdef0123456789abcdef",
        "learnflow.cors.allowed-origins=https://learnflow.example.com"
})
class CookieOriginWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private AuthSessionService authSessionService;
    @MockBean
    private CurrentUserService currentUserService;
    @MockBean
    private LoginAttemptService loginAttemptService;
    @MockBean
    private PasswordResetService passwordResetService;
    @MockBean
    private PasswordResetAttemptService passwordResetAttemptService;

    @Test
    void refreshRejectsMissingOriginBeforeReadingCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshRejectsCrossSiteOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowListedOriginReachesRefreshController() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://learnflow.example.com"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowListedOriginCanLogoutWithoutExistingCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, "https://learnflow.example.com"))
                .andExpect(status().isNoContent());
    }

    @Test
    void anonymousUserCanRequestPasswordResetWithoutLeakingAccountExistence() throws Exception {
        when(passwordResetAttemptService.key(anyString(), anyString())).thenReturn("alice|127.0.0.1");
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.com\"}"))
                .andExpect(status().isAccepted());
    }
}

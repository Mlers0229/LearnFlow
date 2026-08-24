package com.learnflow.controller;

import com.learnflow.config.LearnFlowAuthProperties;
import com.learnflow.dto.AccountErasureRequest;
import com.learnflow.dto.PrivacyRequestResponse;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.PrivacyRequestService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {
    private final PrivacyRequestService privacy;
    private final CurrentUserService currentUser;
    private final LearnFlowAuthProperties authProperties;

    public PrivacyController(PrivacyRequestService privacy, CurrentUserService currentUser,
                             LearnFlowAuthProperties authProperties) {
        this.privacy = privacy;
        this.currentUser = currentUser;
        this.authProperties = authProperties;
    }

    @PostMapping("/exports")
    public ResponseEntity<PrivacyRequestResponse> requestExport(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.accepted().body(
                privacy.requestExport(currentUser.requireUserId(), idempotencyKey));
    }

    @GetMapping("/requests/{requestId}")
    public PrivacyRequestResponse status(@PathVariable UUID requestId) {
        return privacy.status(currentUser.requireUserId(), requestId);
    }

    @GetMapping("/exports/{requestId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID requestId) {
        PrivacyRequestService.ExportArtifact artifact = privacy.download(currentUser.requireUserId(), requestId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(artifact.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-SHA256", artifact.sha256())
                .body(artifact.bytes());
    }

    @PostMapping("/erasure")
    public ResponseEntity<PrivacyRequestResponse> requestErasure(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AccountErasureRequest request,
            HttpServletResponse response) {
        PrivacyRequestResponse result = privacy.requestErasure(currentUser.requireUserId(), idempotencyKey, request);
        clearRefreshCookie(response);
        return ResponseEntity.accepted().body(result);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(authProperties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(authProperties.isSecureCookie())
                .sameSite(authProperties.getSameSite())
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

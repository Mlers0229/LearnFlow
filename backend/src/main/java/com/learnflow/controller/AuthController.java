package com.learnflow.controller;

import com.learnflow.dto.AuthResponse;
import com.learnflow.dto.LoginRequest;
import com.learnflow.dto.RegisterRequest;
import com.learnflow.dto.UpdateProfileRequest;
import com.learnflow.dto.PasswordResetRequest;
import com.learnflow.dto.PasswordResetConfirmRequest;
import com.learnflow.service.AuthService;
import com.learnflow.service.AuthSessionService;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.LoginAttemptService;
import com.learnflow.service.PasswordResetService;
import com.learnflow.service.PasswordResetAttemptService;
import com.learnflow.config.LearnFlowAuthProperties;
import com.learnflow.entity.User;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 登录 / 注册相关接口（简化版，无 token）。
 *
 * 所有接口暂时对外开放，主要用于前端演示和基本身份区分。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService authSessionService;
    private final CurrentUserService currentUserService;
    private final LoginAttemptService loginAttemptService;
    private final LearnFlowAuthProperties authProperties;
    private final PasswordResetService passwordResetService;
    private final PasswordResetAttemptService passwordResetAttemptService;

    public AuthController(
            AuthService authService,
            AuthSessionService authSessionService,
            CurrentUserService currentUserService,
            LoginAttemptService loginAttemptService,
            LearnFlowAuthProperties authProperties,
            PasswordResetService passwordResetService,
            PasswordResetAttemptService passwordResetAttemptService
    ) {
        this.authService = authService;
        this.authSessionService = authSessionService;
        this.currentUserService = currentUserService;
        this.loginAttemptService = loginAttemptService;
        this.authProperties = authProperties;
        this.passwordResetService = passwordResetService;
        this.passwordResetAttemptService = passwordResetAttemptService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        User user = authService.register(request);
        AuthSessionService.IssuedSession session = authSessionService.issue(user);
        writeRefreshCookie(response, session);
        return new ResponseEntity<>(session.response(), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String attemptKey = loginAttemptService.key(request.getUsername(), servletRequest.getRemoteAddr());
        loginAttemptService.assertAllowed(attemptKey);
        try {
            User user = authService.login(request);
            loginAttemptService.recordSuccess(attemptKey);
            AuthSessionService.IssuedSession session = authSessionService.issue(user);
            writeRefreshCookie(servletResponse, session);
            return ResponseEntity.ok(session.response());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                loginAttemptService.recordFailure(attemptKey);
            }
            throw exception;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthSessionService.IssuedSession session = authSessionService.rotate(requireRefreshCookie(request));
        writeRefreshCookie(response, session);
        return ResponseEntity.ok(session.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = findRefreshCookie(request);
        if (token != null) {
            authSessionService.revoke(token);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                     HttpServletRequest servletRequest) {
        String attemptKey = passwordResetAttemptService.key(request.getUsername(), servletRequest.getRemoteAddr());
        passwordResetAttemptService.assertAllowed(attemptKey);
        passwordResetService.request(request.getUsername(), request.getEmail());
        passwordResetAttemptService.recordRequest(attemptKey);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me() {
        User user = authService.requireActiveUser(currentUserService.requireUserId());
        return ResponseEntity.ok(authService.toAuthResponse(user));
    }

    @PatchMapping("/profile")
    public ResponseEntity<AuthResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
        User user = authService.updateProfile(currentUserService.requireUserId(), request);
        return ResponseEntity.ok(authService.toAuthResponse(user));
    }

    private void writeRefreshCookie(HttpServletResponse response, AuthSessionService.IssuedSession session) {
        ResponseCookie cookie = ResponseCookie.from(authProperties.getRefreshCookieName(), session.refreshToken())
                .httpOnly(true)
                .secure(authProperties.isSecureCookie())
                .sameSite(authProperties.getSameSite())
                .path("/api/auth")
                .maxAge(session.refreshMaxAgeSeconds())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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

    private String requireRefreshCookie(HttpServletRequest request) {
        String token = findRefreshCookie(request);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少刷新凭证");
        }
        return token;
    }

    private String findRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authProperties.getRefreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}




package com.learnflow.controller;

import com.learnflow.dto.AuthResponse;
import com.learnflow.dto.LoginRequest;
import com.learnflow.dto.RegisterRequest;
import com.learnflow.dto.UpdateProfileRequest;
import com.learnflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 登录 / 注册相关接口（简化版，无 token）。
 *
 * 所有接口暂时对外开放，主要用于前端演示和基本身份区分。
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse resp = authService.register(request);
        return new ResponseEntity<>(resp, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse resp = authService.login(request);
        return new ResponseEntity<>(resp, HttpStatus.OK);
    }

    @PatchMapping("/profile")
    public ResponseEntity<AuthResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
        AuthResponse resp = authService.updateProfile(request);
        return new ResponseEntity<>(resp, HttpStatus.OK);
    }
}




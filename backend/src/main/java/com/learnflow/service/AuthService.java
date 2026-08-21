package com.learnflow.service;

import com.learnflow.dto.AuthResponse;
import com.learnflow.dto.LoginRequest;
import com.learnflow.dto.RegisterRequest;
import com.learnflow.dto.UpdateProfileRequest;
import com.learnflow.entity.User;
import com.learnflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * 处理注册和登录的业务逻辑。
 *
 * 当前实现非常简化：
 * - 不生成 token，不做真正的鉴权；
 * - 只在登录/注册成功时返回基本用户信息，供前端保存和展示；
 * - 密码使用 BCrypt 进行加密存储。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setLevel(request.getLevel());
        // 角色默认由 User.prePersist 设置为 student

        return userRepository.save(user);
    }

    public User login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已被禁用，请联系管理员");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        return user;
    }

    public User updateProfile(Long currentUserId, UpdateProfileRequest req) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
        if (req.getLevel() != null) {
            user.setLevel(req.getLevel());
        }
        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getNewPassword().length() < 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码至少 12 位");
            }
            if (req.getOldPassword() == null || !passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码不正确");
            }
            user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
            refreshTokenService.revokeAllForUser(currentUserId);
        }

        return userRepository.save(user);
    }

    public User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号已被禁用");
        }
        return user;
    }

    public AuthResponse toAuthResponse(User user) {
        AuthResponse resp = new AuthResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRole(user.getRole());
        resp.setLevel(user.getLevel());
        resp.setStatus(user.getStatus());
        resp.setEmail(user.getEmail());
        return resp;
    }
}




package com.learnflow.service;

import com.learnflow.dto.UserDto;
import com.learnflow.dto.UserUpdateRequest;
import com.learnflow.entity.User;
import com.learnflow.repository.UserRepository;
import com.learnflow.service.AdminAuditLogService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.security.SecureRandom;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final AdminAuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserAdminService(
            UserRepository userRepository,
            AdminAuditLogService auditLogService,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public List<UserDto> listUsers() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    public void updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));

        if (request.getRole() != null && !request.getRole().isBlank()) {
            String role = request.getRole().toLowerCase();
            if (!role.equals("student") && !role.equals("admin")) {
                throw new IllegalArgumentException("invalid role");
            }
            user.setRole(role);
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("DISABLED")) {
                throw new IllegalArgumentException("invalid status");
            }
            user.setStatus(status);
        }

        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        auditLogService.record("USER_UPDATE", "admin", "USER", user.getId(),
                String.format("role=%s,status=%s", user.getRole(), user.getStatus()));
    }

    public UserDto createUser(String username, String email, String role, String level, String rawPassword) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username empty");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("username exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(role == null ? "student" : role);
        user.setLevel(level);
        user.setPasswordHash(passwordEncoder.encode(
                rawPassword == null || rawPassword.isBlank() ? generateTempPassword() : rawPassword
        ));
        user.setStatus("ACTIVE");
        User saved = userRepository.save(user);
        auditLogService.record("USER_CREATE", "admin", "USER", saved.getId(), "created");
        return toDto(saved);
    }

    public String resetPassword(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        String temp = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(temp));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        auditLogService.record("USER_RESET_PWD", "admin", "USER", user.getId(), "reset password");
        return temp;
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setLevel(user.getLevel());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int idx = secureRandom.nextInt(chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }
}



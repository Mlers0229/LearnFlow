package com.learnflow.controller;

import com.learnflow.dto.UserDto;
import com.learnflow.dto.UserUpdateRequest;
import com.learnflow.service.UserAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> list() {
        return ResponseEntity.ok(userAdminService.listUsers());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable("id") Long id,
                                       @RequestBody UserUpdateRequest request) {
        try {
            userAdminService.updateUser(id, request);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody UserDto dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getRole() == null || dto.getRole().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UserDto created = userAdminService.createUser(
                dto.getUsername(),
                dto.getEmail(),
                dto.getRole(),
                dto.getLevel(),
                null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<String> resetPassword(@PathVariable("id") Long id) {
        try {
            String tempPwd = userAdminService.resetPassword(id);
            return ResponseEntity.ok(tempPwd);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}



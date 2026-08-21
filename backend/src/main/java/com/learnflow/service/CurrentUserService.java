package com.learnflow.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {

    public Long requireUserId() {
        JwtAuthenticationToken jwtAuthentication = requireJwtAuthentication();
        try {
            return Long.parseLong(jwtAuthentication.getToken().getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "身份凭证无效");
        }
    }

    public String requireUsername() {
        String username = requireJwtAuthentication().getToken().getClaimAsString("username");
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "身份凭证无效");
        }
        return username;
    }

    public boolean isAdmin() {
        Authentication authentication = requireJwtAuthentication();
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private JwtAuthenticationToken requireJwtAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要登录");
        }
        return jwtAuthentication;
    }
}

package com.learnflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;

    public ActiveAccountFilter(ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.jdbcProvider = jdbcProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.isAuthenticated()
                && jdbc != null) {
            Long userId = parseSubject(jwt.getToken().getSubject());
            Boolean active = userId == null ? Boolean.FALSE : jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM app_user WHERE id = ? AND status = 'ACTIVE')",
                    Boolean.class, userId);
            if (!Boolean.TRUE.equals(active)) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account is unavailable");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Long parseSubject(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

package com.learnflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Protects the endpoints that authenticate only with the refresh-token cookie.
 * Bearer-token API calls are not vulnerable to ambient-cookie CSRF, while these
 * two endpoints are and therefore require an explicitly allow-listed Origin.
 */
public class CookieOriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    private final LearnFlowCorsProperties corsProperties;

    public CookieOriginValidationFilter(LearnFlowCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !PROTECTED_PATHS.contains(resolveApplicationPath(request));
    }

    private String resolveApplicationPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (requestUri == null || contextPath == null || !requestUri.startsWith(contextPath)) {
            return requestUri;
        }
        return requestUri.substring(contextPath.length());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean allowed = origin != null
                && !origin.isBlank()
                && !"null".equalsIgnoreCase(origin)
                && corsProperties.getAllowedOrigins().contains(origin);

        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"forbidden\",\"message\":\"invalid request origin\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}

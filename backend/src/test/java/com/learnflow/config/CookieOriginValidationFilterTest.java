package com.learnflow.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CookieOriginValidationFilterTest {

    private CookieOriginValidationFilter filter;

    @BeforeEach
    void setUp() {
        LearnFlowCorsProperties properties = new LearnFlowCorsProperties();
        properties.setAllowedOrigins(List.of("https://learnflow.example.com"));
        filter = new CookieOriginValidationFilter(properties);
    }

    @Test
    void acceptsAllowListedOriginForRefresh() throws Exception {
        MockHttpServletRequest request = protectedPost("/api/auth/refresh");
        request.addHeader(HttpHeaders.ORIGIN, "https://learnflow.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMissingOriginForRefresh() throws Exception {
        MockHttpServletRequest request = protectedPost("/api/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("invalid request origin");
    }

    @Test
    void rejectsUntrustedOriginForLogout() throws Exception {
        MockHttpServletRequest request = protectedPost("/api/auth/logout");
        request.addHeader(HttpHeaders.ORIGIN, "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void doesNotApplyToBearerApiEndpoints() throws Exception {
        MockHttpServletRequest request = protectedPost("/api/plan");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest protectedPost(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        return request;
    }
}

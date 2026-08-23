package com.learnflow.controller;

import com.learnflow.service.CurrentUserService;
import com.learnflow.service.MasteryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasteryControllerIdentityTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listAndRecomputeAlwaysUseAuthenticatedUser() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", "7", "username", "alice"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        MasteryService service = mock(MasteryService.class);
        when(service.listProfiles(7L, 10)).thenReturn(List.of());
        when(service.recomputeAll(7L, 10)).thenReturn(List.of());
        MasteryController controller = new MasteryController(service, new CurrentUserService());

        controller.list(10);
        controller.recompute(10);

        verify(service).listProfiles(7L, 10);
        verify(service).recomputeAll(7L, 10);
    }
}


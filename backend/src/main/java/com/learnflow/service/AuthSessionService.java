package com.learnflow.service;

import com.learnflow.dto.AuthResponse;
import com.learnflow.entity.User;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthService authService;

    public AuthSessionService(JwtService jwtService, RefreshTokenService refreshTokenService, AuthService authService) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authService = authService;
    }

    public IssuedSession issue(User user) {
        JwtService.IssuedAccessToken accessToken = jwtService.issue(user);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.create(user);
        return createSession(user, accessToken, refreshToken);
    }

    public IssuedSession rotate(String rawRefreshToken) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
        JwtService.IssuedAccessToken accessToken = jwtService.issue(rotated.user());
        return createSession(rotated.user(), accessToken, rotated.refreshToken());
    }

    public void revoke(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private IssuedSession createSession(
            User user,
            JwtService.IssuedAccessToken accessToken,
            RefreshTokenService.IssuedRefreshToken refreshToken
    ) {
        AuthResponse response = authService.toAuthResponse(user);
        response.setAccessToken(accessToken.value());
        response.setExpiresInSeconds(accessToken.expiresInSeconds());
        return new IssuedSession(response, refreshToken.value(), refreshToken.maxAgeSeconds());
    }

    public record IssuedSession(AuthResponse response, String refreshToken, long refreshMaxAgeSeconds) {}
}

package com.learnflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({LearnFlowCorsProperties.class, LearnFlowSecurityProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            LearnFlowSecurityProperties securityProperties,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            CookieOriginValidationFilter cookieOriginValidationFilter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .addFilterBefore(cookieOriginValidationFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> {
                    if (securityProperties.isEnforceAuthentication()) {
                        authorize
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers(
                                        "/api/auth/login",
                                        "/api/auth/register",
                                        "/api/auth/password-reset/request",
                                        "/api/auth/password-reset/confirm",
                                        "/api/auth/refresh",
                                        "/api/auth/logout",
                                        "/v3/api-docs/**",
                                        "/actuator/health"
                                ).permitAll()
                                .requestMatchers("/actuator/**").hasRole("ADMIN")
                                .anyRequest().authenticated();
                    } else {
                        // Compatibility window only. Sprint 2 replaces this branch with JWT/RBAC enforcement.
                        authorize.anyRequest().permitAll();
                    }
                });
        return http.build();
    }

    @Bean
    public CookieOriginValidationFilter cookieOriginValidationFilter(LearnFlowCorsProperties properties) {
        return new CookieOriginValidationFilter(properties);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(LearnFlowCorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "Idempotency-Key",
                "X-Requested-With",
                "X-CSRF-Token"
        ));
        configuration.setExposedHeaders(List.of(HttpHeaders.LOCATION, "X-Request-Id", "X-Trace-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

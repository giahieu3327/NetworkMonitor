package com.network_monitor.portal_service.config;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // =========================================================
    // SECURITY FILTER CHAIN
    // =========================================================

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // -------------------------------------------------
                // CORS
                // -------------------------------------------------
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                // -------------------------------------------------
                // CSRF
                // -------------------------------------------------
                .csrf(csrf -> csrf.disable())

                // -------------------------------------------------
                // SESSION
                // -------------------------------------------------
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // -------------------------------------------------
                // AUTHORIZATION
                // -------------------------------------------------
                .authorizeHttpRequests(auth -> auth

                        // ================================
                        // OPTIONS
                        // ================================
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        // ================================
                        // SWAGGER
                        // ================================
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**"
                        ).permitAll()

                        // ================================
                        // ACTUATOR
                        // ================================
                        .requestMatchers(
                                "/actuator/**"
                        ).permitAll()

                        // ================================
                        // PUBLIC AUTH API
                        // ================================
                        .requestMatchers(
                                HttpMethod.POST,

                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",

                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/verify-reset-password-otp",
                                "/api/v1/auth/reset-password",

                                "/api/v1/auth/send-verification-email",
                                "/api/v1/auth/verify-email"
                        ).permitAll()

                        // ================================
                        // CAMUNDA
                        // ================================
                        .requestMatchers(
                                "/camunda/**",
                                "/camunda-welcome/**",
                                "/engine-rest/**"
                        ).permitAll()

                        // ================================
                        // EVERYTHING ELSE
                        // ================================
                        .anyRequest().authenticated()
                )

                // -------------------------------------------------
                // OAUTH2 RESOURCE SERVER
                // -------------------------------------------------
                .oauth2ResourceServer(oauth2 -> oauth2

                        /*
                         * QUAN TRỌNG:
                         *
                         * Public API sẽ không lấy Bearer Token.
                         *
                         * Vì vậy nếu frontend gửi:
                         *
                         * Authorization: Bearer <expired-token>
                         *
                         * vào public API thì token sẽ bị bỏ qua.
                         *
                         * API vẫn chạy bình thường theo permitAll().
                         */
                        .bearerTokenResolver(
                                bearerTokenResolver()
                        )

                        .jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                );

        return http.build();
    }


    // =========================================================
    // BEARER TOKEN RESOLVER
    // =========================================================

    @Bean
    public BearerTokenResolver bearerTokenResolver() {

        DefaultBearerTokenResolver defaultResolver =
                new DefaultBearerTokenResolver();

        return new BearerTokenResolver() {

            @Override
            public String resolve(HttpServletRequest request) {

                String method =
                        request.getMethod();

                String uri =
                        request.getRequestURI();

                // =============================================
                // OPTIONS
                // =============================================

                if ("OPTIONS".equalsIgnoreCase(method)) {
                    return null;
                }


                // =============================================
                // SWAGGER
                // =============================================

                if (
                        uri.equals("/swagger-ui.html")
                                || uri.startsWith("/swagger-ui/")
                                || uri.startsWith("/v3/api-docs/")
                                || uri.startsWith("/api-docs/")
                ) {
                    return null;
                }


                // =============================================
                // ACTUATOR
                // =============================================

                if (uri.startsWith("/actuator/")) {
                    return null;
                }


                // =============================================
                // PUBLIC AUTH API
                // =============================================

                if (
                        "POST".equalsIgnoreCase(method)
                                && (
                                uri.equals("/api/v1/auth/login")
                                        || uri.equals("/api/v1/auth/refresh")
                                        || uri.equals("/api/v1/auth/logout")

                                        || uri.equals(
                                        "/api/v1/auth/forgot-password"
                                )

                                        || uri.equals(
                                        "/api/v1/auth/verify-reset-password-otp"
                                )

                                        || uri.equals(
                                        "/api/v1/auth/reset-password"
                                )

                                        || uri.equals(
                                        "/api/v1/auth/send-verification-email"
                                )

                                        || uri.equals(
                                        "/api/v1/auth/verify-email"
                                )
                        )
                ) {
                    return null;
                }


                // =============================================
                // CAMUNDA PUBLIC API
                // =============================================

                if (
                        uri.startsWith("/camunda/")
                                || uri.startsWith("/camunda-welcome/")
                                || uri.startsWith("/engine-rest/")
                ) {
                    return null;
                }


                // =============================================
                // OTHER API
                // =============================================

                /*
                 * Những API không nằm trong danh sách public
                 * vẫn phải đọc Bearer Token bình thường.
                 *
                 * Nếu token hết hạn:
                 *
                 *     → 401
                 *
                 * Nếu token hợp lệ:
                 *
                 *     → tiếp tục authenticate
                 */
                return defaultResolver.resolve(request);
            }
        };
    }


    // =========================================================
    // CORS
    // =========================================================

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOriginPatterns(
                List.of("*")
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "PATCH",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }


    // =========================================================
    // JWT AUTHENTICATION CONVERTER
    // =========================================================

    private Converter<Jwt, AbstractAuthenticationToken>
    jwtAuthenticationConverter() {

        JwtAuthenticationConverter jwtConverter =
                new JwtAuthenticationConverter();

        jwtConverter.setJwtGrantedAuthoritiesConverter(
                new KeycloakGrantedAuthoritiesConverter()
        );

        return jwtConverter;
    }


    // =========================================================
    // KEYCLOAK ROLE CONVERTER
    // =========================================================

    private static class KeycloakGrantedAuthoritiesConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {

            Map<String, Object> realmAccess =
                    jwt.getClaim("realm_access");

            if (
                    realmAccess == null
                            || !realmAccess.containsKey("roles")
            ) {
                return Collections.emptyList();
            }

            Object rolesObject =
                    realmAccess.get("roles");

            if (!(rolesObject instanceof List<?> rolesList)) {
                return Collections.emptyList();
            }

            return rolesList.stream()

                    .filter(String.class::isInstance)

                    .map(String.class::cast)

                    .map(role ->
                            role.startsWith("ROLE_")
                                    ? role
                                    : "ROLE_" + role
                    )

                    .map(SimpleGrantedAuthority::new)

                    .collect(Collectors.toList());
        }
    }
}
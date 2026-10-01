package com.network_monitor.portal_service.config;

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

    /**
     * ============================================================
     * SECURITY FILTER CHAIN
     * ============================================================
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // ====================================================
                // CORS
                // ====================================================
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ====================================================
                // CSRF
                // REST API dùng JWT -> disable CSRF
                // ====================================================
                .csrf(csrf -> csrf.disable())

                // ====================================================
                // SESSION
                // JWT Stateless -> không lưu session
                // ====================================================
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ====================================================
                // AUTHORIZATION
                // ====================================================
                .authorizeHttpRequests(auth -> auth

                        // ------------------------------------------------
                        // 1. CORS Pre-flight
                        // ------------------------------------------------
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()

                        // ------------------------------------------------
                        // 2. Swagger / OpenAPI
                        // ------------------------------------------------
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**"
                        )
                        .permitAll()

                        // ------------------------------------------------
                        // 3. Spring Boot Actuator
                        // ------------------------------------------------
                        .requestMatchers("/actuator/**")
                        .permitAll()

                        // ------------------------------------------------
                        // 4. Authentication API
                        // ------------------------------------------------

                        // Login
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login"
                        )
                        .permitAll()

                        // Refresh token
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/refresh"
                        )
                        .permitAll()

                        // Forgot password -> gửi OTP
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/forgot-password"
                        )
                        .permitAll()

                        // Reset password
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/reset-password"
                        )
                        .permitAll()

                        // ------------------------------------------------
                        // 5. Email verification / password reset OTP
                        // ------------------------------------------------

                        // Gửi email xác thực
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/emails/send-verification-email"
                        )
                        .permitAll()

                        // Xác thực email bằng token
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/emails/verify-verification-email"
                        )
                        .permitAll()

                        // Gửi OTP reset password
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/emails/send-reset-password-otp"
                        )
                        .permitAll()

                        // Xác thực OTP reset password
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/emails/verify-reset-password-otp"
                        )
                        .permitAll()

                        // ------------------------------------------------
                        // 6. Camunda
                        // ------------------------------------------------
                        .requestMatchers(
                                "/camunda/**",
                                "/camunda-welcome/**",
                                "/engine-rest/**"
                        )
                        .permitAll()

                        // ------------------------------------------------
                        // 7. Tất cả API còn lại phải đăng nhập
                        // ------------------------------------------------
                        .anyRequest()
                        .authenticated()
                )

                // ====================================================
                // OAUTH2 RESOURCE SERVER
                // Keycloak JWT
                // ====================================================
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                );

        return http.build();
    }

    /**
     * ============================================================
     * CORS CONFIGURATION
     * ============================================================
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

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

    /**
     * ============================================================
     * JWT AUTHENTICATION CONVERTER
     * ============================================================
     */
    private Converter<Jwt, AbstractAuthenticationToken>
    jwtAuthenticationConverter() {

        JwtAuthenticationConverter jwtConverter =
                new JwtAuthenticationConverter();

        jwtConverter.setJwtGrantedAuthoritiesConverter(
                new KeycloakGrantedAuthoritiesConverter()
        );

        return jwtConverter;
    }

    /**
     * ============================================================
     * KEYCLOAK ROLE -> SPRING SECURITY AUTHORITY
     * ============================================================
     *
     * Keycloak:
     *
     * ROLE_SUPER_ADMIN
     * ROLE_NETWORK_ADMIN
     * ROLE_NOC_OPERATOR
     *
     * Spring Security:
     *
     * ROLE_SUPER_ADMIN
     * ROLE_NETWORK_ADMIN
     * ROLE_NOC_OPERATOR
     *
     * Controller có thể dùng:
     *
     * @PreAuthorize("hasRole('SUPER_ADMIN')")
     *
     * hoặc:
     *
     * @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
     */
    private static class KeycloakGrantedAuthoritiesConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {

            Map<String, Object> realmAccess =
                    jwt.getClaim("realm_access");

            if (realmAccess == null ||
                    !realmAccess.containsKey("roles")) {

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
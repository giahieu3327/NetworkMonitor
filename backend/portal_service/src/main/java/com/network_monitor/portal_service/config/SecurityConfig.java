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
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    private static final String ROLE_NETWORK_ADMIN = "ROLE_NETWORK_ADMIN";
    private static final String ROLE_NOC_OPERATOR = "ROLE_NOC_OPERATOR";
    private static final String ROLE_REPORT_MANAGER = "ROLE_REPORT_MANAGER";

    private static final String[] NON_GUEST_ROLES = {
            ROLE_SUPER_ADMIN,
            ROLE_NETWORK_ADMIN,
            ROLE_NOC_OPERATOR,
            ROLE_REPORT_MANAGER
    };

    private static final String[] INFRA_ADMIN_ROLES = {
            ROLE_SUPER_ADMIN,
            ROLE_NETWORK_ADMIN
    };

    private static final String[] TICKET_REVIEWER_ROLES = {
            ROLE_SUPER_ADMIN,
            ROLE_NOC_OPERATOR
    };

    private static final String[] REPORT_ROLES = {
            ROLE_SUPER_ADMIN,
            ROLE_REPORT_MANAGER
    };

    private static final Set<String> PUBLIC_AUTH_ENDPOINTS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/verify-reset-password-otp",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/send-verification-email",
            "/api/v1/auth/verify-email"
    );

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**",
                                "/actuator/**",
                                "/camunda/**",
                                "/camunda-welcome/**",
                                "/engine-rest/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                PUBLIC_AUTH_ENDPOINTS.toArray(new String[0])
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/export")
                        .hasAnyAuthority(NON_GUEST_ROLES)

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/auth/me",
                                "/api/v1/auth/profile"
                        ).authenticated()

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/auth/change-password",
                                "/api/v1/auth/profile"
                        ).authenticated()

                        .requestMatchers("/api/v1/auth/users/**", "/api/v1/users/**", "/api/v1/roles/**")
                        .hasAuthority(ROLE_SUPER_ADMIN)

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/devices/export",
                                "/api/v1/devices/*/credentials/export",
                                "/api/v1/devices/*/oid-configs/export",
                                "/api/v1/devices/*/threshold-rules/export",
                                "/api/v1/device-credentials/export",
                                "/api/v1/oid-configs/export",
                                "/api/v1/threshold-rules/export"
                        ).hasAnyAuthority(NON_GUEST_ROLES)

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/devices/**",
                                "/api/v1/device-credentials/**",
                                "/api/v1/oid-configs/**",
                                "/api/v1/threshold-rules/**"
                        ).hasAnyAuthority(INFRA_ADMIN_ROLES)

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/devices/**",
                                "/api/v1/device-credentials/**",
                                "/api/v1/oid-configs/**",
                                "/api/v1/threshold-rules/**"
                        ).hasAnyAuthority(INFRA_ADMIN_ROLES)

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/devices/**",
                                "/api/v1/device-credentials/**",
                                "/api/v1/oid-configs/**",
                                "/api/v1/threshold-rules/**"
                        ).hasAnyAuthority(INFRA_ADMIN_ROLES)

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/devices/**",
                                "/api/v1/device-credentials/**",
                                "/api/v1/oid-configs/**",
                                "/api/v1/threshold-rules/**"
                        ).hasAnyAuthority(INFRA_ADMIN_ROLES)

                        .requestMatchers(HttpMethod.POST, "/api/v1/tickets/*/review")
                        .hasAnyAuthority(TICKET_REVIEWER_ROLES)

                        .requestMatchers(HttpMethod.POST, "/api/v1/tickets/*/cancel")
                        .hasAnyAuthority(INFRA_ADMIN_ROLES)

                        .requestMatchers("/api/v1/tickets/**")
                        .hasAnyAuthority(NON_GUEST_ROLES)

                        .requestMatchers(HttpMethod.POST, "/api/v1/incidents/**")
                        .hasAnyAuthority(ROLE_SUPER_ADMIN, ROLE_NETWORK_ADMIN, ROLE_NOC_OPERATOR)

                        .requestMatchers(HttpMethod.PUT, "/api/v1/incidents/**")
                        .hasAnyAuthority(ROLE_SUPER_ADMIN, ROLE_NETWORK_ADMIN, ROLE_NOC_OPERATOR)

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/incidents/**")
                        .hasAnyAuthority(ROLE_SUPER_ADMIN, ROLE_NETWORK_ADMIN, ROLE_NOC_OPERATOR)

                        .requestMatchers(HttpMethod.DELETE, "/api/v1/incidents/**")
                        .hasAnyAuthority(INFRA_ADMIN_ROLES)

                        .requestMatchers(HttpMethod.GET, "/api/v1/reports/**")
                        .hasAnyAuthority(NON_GUEST_ROLES)

                        .requestMatchers("/api/v1/reports/**")
                        .hasAnyAuthority(REPORT_ROLES)

                        .requestMatchers(HttpMethod.GET, "/api/v1/**")
                        .authenticated()

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {

        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();

        return new BearerTokenResolver() {

            @Override
            public String resolve(HttpServletRequest request) {

                String method = request.getMethod();
                String uri = request.getRequestURI();

                if ("OPTIONS".equalsIgnoreCase(method)) {
                    return null;
                }

                if (
                        uri.equals("/swagger-ui.html")
                                || uri.startsWith("/swagger-ui/")
                                || uri.startsWith("/v3/api-docs")
                                || uri.startsWith("/api-docs")
                                || uri.startsWith("/actuator")
                                || uri.startsWith("/camunda")
                                || uri.startsWith("/camunda-welcome")
                                || uri.startsWith("/engine-rest")
                ) {
                    return null;
                }

                if ("POST".equalsIgnoreCase(method) && PUBLIC_AUTH_ENDPOINTS.contains(uri)) {
                    return null;
                }

                return defaultResolver.resolve(request);
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakGrantedAuthoritiesConverter());

        return jwtConverter;
    }

    private static class KeycloakGrantedAuthoritiesConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");

            if (realmAccess == null || !realmAccess.containsKey("roles")) {
                return Collections.emptyList();
            }

            Object rolesObject = realmAccess.get("roles");

            if (!(rolesObject instanceof List<?> rolesList)) {
                return Collections.emptyList();
            }

            return rolesList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
    }
}
package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.*;
import com.network_monitor.portal_service.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final KeyCloakService keyCloakService;
    private final UserService userService;

    private static final String ROOT_ADMIN_USERNAME = "admin";
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    @Transactional
    public User register(RegisterRequest request, String currentUsername) {
        if (userService.existsByUsername(request.getUsername()) || keyCloakService.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username '" + request.getUsername() + "' đã tồn tại trong hệ thống!");
        }

        String targetRole = request.getRole().trim();
        if (ROLE_SUPER_ADMIN.equalsIgnoreCase(targetRole)) {
            if (!ROOT_ADMIN_USERNAME.equalsIgnoreCase(currentUsername)) {
                throw new AccessDeniedException("Chỉ duy nhất tài khoản Admin tối cao (username: admin) mới có quyền gán ROLE_SUPER_ADMIN!");
            }
        }

        String keycloakId = keyCloakService.createAndConfigureUser(
                request.getUsername(),
                request.getEmail(),
                request.getFullName(),
                request.getPassword(),
                targetRole
        );

        return userService.createLocalUser(
                keycloakId,
                request.getUsername(),
                request.getEmail(),
                request.getFullName(),
                request.getPhoneNumber()
        );
    }

    public TokenResponse login(LoginRequest request) {
        return keyCloakService.login(request.getUsername(), request.getPassword());
    }

    public TokenResponse refreshToken(RefreshTokenRequest request) {
        return keyCloakService.refreshToken(request.getRefreshToken());
    }

    public void logout(LogoutRequest request) {
        keyCloakService.logout(request.getRefreshToken());
    }

    public UserProfileResponse getMe(org.springframework.security.oauth2.jwt.Jwt jwt) {
        String userId = jwt.getSubject(); // Claim 'sub' chính là Keycloak UUID / Postgres User ID

        List<String> roles = java.util.Collections.emptyList();
        java.util.Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> realmRoles = (List<String>) realmAccess.get("roles");
            
            // Chỉ lấy những role bắt đầu bằng tiền tố "ROLE_"
            roles = realmRoles.stream()
                    .filter(role -> role != null && role.startsWith("ROLE_"))
                    .collect(java.util.stream.Collectors.toList());
        }

        return userService.getCurrentUserProfile(userId, roles);
    }

    public void changePassword(org.springframework.security.oauth2.jwt.Jwt jwt, com.network_monitor.portal_service.model.dto.ChangePasswordRequest request) {
        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");

        if (username == null) {
            username = jwt.getClaimAsString("sub");
        }

        keyCloakService.changePassword(userId, username, request.getCurrentPassword(), request.getNewPassword());
    }
}
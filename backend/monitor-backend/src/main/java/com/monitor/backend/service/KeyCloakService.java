package com.monitor.backend.service;

import com.monitor.backend.model.dto.TokenResponse;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class KeyCloakService {

    @Value("${keycloak.server-url:http://localhost:8080}")
    private String serverUrl;

    @Value("${keycloak.realm:monitor-realm}")
    private String realm;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    @Value("${keycloak.admin.client-id:admin-cli}")
    private String adminClientId;

    @Value("${keycloak.user-client-id:monitor-frontend}")
    private String userClientId;

    public Keycloak getKeycloakInstance() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .username(adminUsername)
                .password(adminPassword)
                .clientId(adminClientId)
                .build();
    }

    public RealmResource getRealmResource() {
        return getKeycloakInstance().realm(realm);
    }

    public UsersResource getUsersResource() {
        return getRealmResource().users();
    }

    public boolean existsByUsername(String username) {
        List<UserRepresentation> users = getUsersResource().searchByUsername(username, true);
        return !users.isEmpty();
    }

    public Optional<UserRepresentation> findByUsername(String username) {
        List<UserRepresentation> users = getUsersResource().searchByUsername(username, true);
        return users.stream().findFirst();
    }

    public Optional<String> findUserIdByUsername(String username) {
        return findByUsername(username).map(UserRepresentation::getId);
    }

    // Nhận fullName, tự động tách ra firstName & lastName để set cho UserRepresentation của Keycloak
    public String createUser(String username, String email, String fullName) {
        String[] nameParts = splitFullName(fullName);
        String lastName = nameParts[0];
        String firstName = nameParts[1];

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        Response response = getUsersResource().create(user);
        if (response.getStatus() != 201) {
            log.error("Failed to create user {} in Keycloak. Status: {}", username, response.getStatus());
            throw new RuntimeException("Keycloak user creation failed with status: " + response.getStatus());
        }

        String path = response.getLocation().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public void setPassword(String userId, String password, boolean temporary) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(temporary);

        UserResource userResource = getUsersResource().get(userId);
        userResource.resetPassword(credential);
    }

    public void assignRealmRole(String userId, String roleName) {
        RoleRepresentation role = getRealmResource().roles().get(roleName).toRepresentation();
        UserResource userResource = getUsersResource().get(userId);
        userResource.roles().realmLevel().add(Collections.singletonList(role));
    }

    public String createAndConfigureUser(String username, String email, String fullName, String password, String roleName) {
        String userId = createUser(username, email, fullName);
        setPassword(userId, password, false);
        assignRealmRole(userId, roleName);
        return userId;
    }

    public TokenResponse login(String username, String password) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "password");
        map.add("client_id", userClientId);
        map.add("username", username);
        map.add("password", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(tokenUrl, request, TokenResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Login failed for user {}: {}", username, e.getMessage());
            throw new RuntimeException("Tên đăng nhập hoặc mật khẩu không chính xác!");
        }
    }

    public TokenResponse refreshToken(String refreshToken) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "refresh_token");
        map.add("client_id", userClientId);
        map.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(tokenUrl, request, TokenResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Refresh token failed: {}", e.getMessage());
            throw new RuntimeException("Refresh token không hợp lệ hoặc đã hết hạn!");
        }
    }

    public void logout(String refreshToken) {
        String logoutUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", userClientId);
        map.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            restTemplate.postForEntity(logoutUrl, request, String.class);
            log.info("Logout successfully on Keycloak");
        } catch (Exception e) {
            log.error("Logout failed on Keycloak: {}", e.getMessage());
            throw new RuntimeException("Đăng xuất thất bại hoặc Refresh Token không hợp lệ!");
        }
    }

    public List<com.monitor.backend.model.dto.RoleResponse> getAllRealmRoles() {
        // Các role mặc định của Keycloak cần lọc bỏ
        List<String> defaultSystemRoles = List.of(
                "offline_access",
                "uma_authorization",
                "default-roles-monitor-realm"
        );

        return getRealmResource().roles().list().stream()
                .filter(role -> !defaultSystemRoles.contains(role.getName()))
                .map(role -> com.monitor.backend.model.dto.RoleResponse.builder()
                        .name(role.getName())
                        .description(role.getDescription())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    public void setUserEnabled(String userId, boolean enabled) {
        try {
            UserResource userResource = getUsersResource().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(enabled);
            userResource.update(user);
            log.info("Cập nhật trạng thái Keycloak user {} thành enabled={}", userId, enabled);
        } catch (Exception e) {
            log.error("Lỗi cập nhật trạng thái Keycloak user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Cập nhật trạng thái tài khoản trên Keycloak thất bại: " + e.getMessage());
        }
    }

    public void changePassword(String userId, String username, String currentPassword, String newPassword) {
        // 1. Xác thực mật khẩu cũ bằng cách thử đăng nhập
        try {
            login(username, currentPassword);
        } catch (Exception e) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác!");
        }

        // 2. Cập nhật mật khẩu mới trên Keycloak (temporary = false)
        setPassword(userId, newPassword, false);
        log.info("Đổi mật khẩu thành công cho user: {}", username);
    }

    public void deleteUser(String userId) {
        try {
            UserResource userResource = getUsersResource().get(userId);
            userResource.remove();
            log.info("Xóa thành công user {} khỏi Keycloak", userId);
        } catch (Exception e) {
            log.error("Lỗi xóa user {} trên Keycloak: {}", userId, e.getMessage());
            throw new RuntimeException("Xóa tài khoản trên Keycloak thất bại: " + e.getMessage());
        }
    }

    public void updateKeycloakUser(String userId, String email, String fullName) {
        String[] nameParts = splitFullName(fullName);
        String lastName = nameParts[0];
        String firstName = nameParts[1];

        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);

        userResource.update(user);
        log.info("Cập nhật thông tin Keycloak user {} thành công", userId);
    }

    public void updateUserRole(String userId, String newRoleName) {
        UserResource userResource = getUsersResource().get(userId);
        
        // 1. Lấy danh sách role hiện tại trên realm và xóa hết các realm role cũ
        List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listAll();
        if (!currentRoles.isEmpty()) {
            userResource.roles().realmLevel().remove(currentRoles);
        }

        // 2. Gán role mới
        RoleRepresentation newRole = getRealmResource().roles().get(newRoleName).toRepresentation();
        userResource.roles().realmLevel().add(Collections.singletonList(newRole));
        log.info("Cập nhật role cho user {} thành {}", userId, newRoleName);
    }

    // Hàm tiện ích nội bộ hỗ trợ tách fullName thành [lastName, firstName] cho Keycloak
    private String[] splitFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return new String[]{"", ""};
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return new String[]{"", parts[0]};
        }
        String lastName = parts[0];
        StringBuilder firstNameBuilder = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            firstNameBuilder.append(parts[i]).append(i == parts.length - 1 ? "" : " ");
        }
        return new String[]{lastName, firstNameBuilder.toString()};
    }  

}
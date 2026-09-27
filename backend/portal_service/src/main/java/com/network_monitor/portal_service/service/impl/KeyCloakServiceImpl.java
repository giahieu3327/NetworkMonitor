package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.RoleResponse;
import com.network_monitor.portal_service.model.dto.response.TokenResponse;
import com.network_monitor.portal_service.service.KeyCloakService;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class KeyCloakServiceImpl implements KeyCloakService {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    @Value("${keycloak.admin.client-id}")
    private String adminClientId;

    @Value("${keycloak.user-client-id}")
    private String userClientId;

    @Override
    public Keycloak getKeycloakInstance() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .username(adminUsername)
                .password(adminPassword)
                .clientId(adminClientId)
                .build();
    }

    @Override
    public RealmResource getRealmResource() {
        return getKeycloakInstance().realm(realm);
    }

    @Override
    public UsersResource getUsersResource() {
        return getRealmResource().users();
    }

    @Override
    public ApiResponse<Boolean> existsByUsername(String username) {
        try {
            List<UserRepresentation> users = getUsersResource().searchByUsername(username, true);
            return ApiResponse.success("Kiểm tra sự tồn tại của tên đăng nhập thành công", !users.isEmpty());
        } catch (Exception e) {
            log.error("Lỗi kiểm tra username {}: {}", username, e.getMessage());
            return ApiResponse.error("Kiểm tra tên đăng nhập thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<UserRepresentation> findByUsername(String username) {
        try {
            List<UserRepresentation> users = getUsersResource().searchByUsername(username, true);
            Optional<UserRepresentation> user = users.stream().findFirst();
            return user.map(userRepresentation -> ApiResponse.success("Lấy thông tin Keycloak user thành công", userRepresentation))
                    .orElseGet(() -> ApiResponse.error("Không tìm thấy người dùng", "User " + username + " không tồn tại"));
        } catch (Exception e) {
            log.error("Lỗi tìm kiếm user {}: {}", username, e.getMessage());
            return ApiResponse.error("Tìm kiếm người dùng thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<String> findUserIdByUsername(String username) {
        ApiResponse<UserRepresentation> response = findByUsername(username);
        if (response.isSuccess() && response.getData() != null) {
            return ApiResponse.success("Lấy ID người dùng thành công", response.getData().getId());
        }
        return ApiResponse.error("Không lấy được ID người dùng", response.getErrorDetails());
    }

    @Override
    public ApiResponse<TokenResponse> login(String username, String password) {
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
            return ApiResponse.success("Đăng nhập thành công", response.getBody());
        } catch (Exception e) {
            log.error("Login failed for user {}: {}", username, e.getMessage());
            return ApiResponse.error("Đăng nhập thất bại", "Tên đăng nhập hoặc mật khẩu không chính xác!");
        }
    }

    @Override
    public ApiResponse<TokenResponse> refreshToken(String refreshToken) {
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
            return ApiResponse.success("Làm mới token thành công", response.getBody());
        } catch (Exception e) {
            log.error("Refresh token failed: {}", e.getMessage());
            return ApiResponse.error("Refresh token thất bại", "Refresh token không hợp lệ hoặc đã hết hạn!");
        }
    }

    @Override
    public ApiResponse<Void> logout(String refreshToken) {
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
            return ApiResponse.success("Đăng xuất thành công");
        } catch (Exception e) {
            log.error("Logout failed on Keycloak: {}", e.getMessage());
            return ApiResponse.error("Đăng xuất thất bại", "Refresh Token không hợp lệ!");
        }
    }

    @Override
    public ApiResponse<String> createUser(String username, String email, String fullName) {
        try {
            String[] nameParts = splitFullName(fullName);
            String lastName = nameParts[0];
            String firstName = nameParts[1];

            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true); // Đã tạo mail nội bộ Stalwart nên set true sẵn

            Response response = getUsersResource().create(user);
            if (response.getStatus() != 201) {
                log.error("Failed to create user {} in Keycloak. Status: {}", username, response.getStatus());
                return ApiResponse.error("Tạo user trên Keycloak thất bại", "Mã trạng thái Keycloak: " + response.getStatus());
            }

            String path = response.getLocation().getPath();
            String createdUserId = path.substring(path.lastIndexOf('/') + 1);
            return ApiResponse.success("Tạo user trên Keycloak thành công", createdUserId);
        } catch (Exception e) {
            log.error("Lỗi tạo user {}: {}", username, e.getMessage());
            return ApiResponse.error("Tạo user trên Keycloak thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> setPassword(String userId, String password, boolean temporary) {
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(temporary);

            UserResource userResource = getUsersResource().get(userId);
            userResource.resetPassword(credential);
            return ApiResponse.success("Cấu hình mật khẩu thành công");
        } catch (Exception e) {
            log.error("Lỗi đặt mật khẩu user {}: {}", userId, e.getMessage());
            return ApiResponse.error("Cấu hình mật khẩu thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> assignRealmRole(String userId, String roleName) {
        try {
            RoleRepresentation role = getRealmResource().roles().get(roleName).toRepresentation();
            UserResource userResource = getUsersResource().get(userId);
            userResource.roles().realmLevel().add(Collections.singletonList(role));
            return ApiResponse.success("Gán role " + roleName + " thành công");
        } catch (Exception e) {
            log.error("Lỗi gán role cho user {}: {}", userId, e.getMessage());
            return ApiResponse.error("Gán role thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<String> createAndConfigureUser(String username, String email, String fullName, String password, String roleName, boolean sendVerificationEmail) {
        ApiResponse<String> createResponse = createUser(username, email, fullName);
        if (!createResponse.isSuccess()) {
            return createResponse;
        }

        String userId = createResponse.getData();

        ApiResponse<Void> setPwdResponse = setPassword(userId, password, false);
        if (!setPwdResponse.isSuccess()) {
            return ApiResponse.error("Cấu hình người dùng thất bại", setPwdResponse.getErrorDetails());
        }

        if (roleName != null && !roleName.isBlank()) {
            ApiResponse<Void> assignRoleResponse = assignRealmRole(userId, roleName);
            if (!assignRoleResponse.isSuccess()) {
                return ApiResponse.error("Cấu hình role thất bại", assignRoleResponse.getErrorDetails());
            }
        }

        // Nếu truyền true mới gửi mail xác thực (Mặc định truyền false do dùng mail nội bộ Stalwart)
        if (sendVerificationEmail) {
            sendVerificationEmail(userId);
        }

        return ApiResponse.success("Khởi tạo và cấu hình tài khoản thành công", userId);
    }

    @Override
    public ApiResponse<Void> sendVerificationEmail(String userId) {
        try {
            UserResource userResource = getUsersResource().get(userId);
            userResource.sendVerifyEmail();
            log.info("Đã gửi email xác thực đến user Keycloak ID: {}", userId);
            return ApiResponse.success("Email xác thực đã được gửi thành công");
        } catch (Exception e) {
            log.error("Lỗi gửi email xác thực cho user {}: {}", userId, e.getMessage());
            return ApiResponse.error("Gửi email xác thực thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> sendResetPasswordEmail(String userId) {
        try {
            UserResource userResource = getUsersResource().get(userId);
            userResource.executeActionsEmail(Collections.singletonList("UPDATE_PASSWORD"));
            log.info("Đã gửi email đặt lại mật khẩu đến user Keycloak ID: {}", userId);
            return ApiResponse.success("Email đặt lại mật khẩu đã được gửi thành công");
        } catch (Exception e) {
            log.error("Lỗi gửi email đặt lại mật khẩu cho user {}: {}", userId, e.getMessage());
            return ApiResponse.error("Gửi email đặt lại mật khẩu thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> changePassword(String userId, String username, String currentPassword, String newPassword) {
        ApiResponse<TokenResponse> loginCheck = login(username, currentPassword);
        if (!loginCheck.isSuccess()) {
            return ApiResponse.error("Đổi mật khẩu thất bại", "Mật khẩu hiện tại không chính xác!");
        }

        return setPassword(userId, newPassword, false);
    }

    @Override
    public ApiResponse<Void> updateKeycloakUser(String userId, String email, String fullName) {
        try {
            String[] nameParts = splitFullName(fullName);
            String lastName = nameParts[0];
            String firstName = nameParts[1];

            UserResource userResource = getUsersResource().get(userId);
            UserRepresentation user = userResource.toRepresentation();

            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
            }

            user.setFirstName(firstName);
            user.setLastName(lastName);

            userResource.update(user);
            log.info("Cập nhật thông tin Keycloak user {} thành công", userId);
            return ApiResponse.success("Cập nhật thông tin trên Keycloak thành công");
        } catch (Exception e) {
            log.error("Lỗi cập nhật Keycloak user {}: {}", userId, e.getMessage());
            return ApiResponse.error("Cập nhật thông tin trên Keycloak thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> updateUserRole(String userId, String newRoleName) {
        try {
            UserResource userResource = getUsersResource().get(userId);

            List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listAll();
            if (!currentRoles.isEmpty()) {
                userResource.roles().realmLevel().remove(currentRoles);
            }

            RoleRepresentation newRole = getRealmResource().roles().get(newRoleName).toRepresentation();
            userResource.roles().realmLevel().add(Collections.singletonList(newRole));
            log.info("Cập nhật role cho user {} thành {}", userId, newRoleName);
            return ApiResponse.success("Cập nhật role thành công");
        } catch (Exception e) {
            log.error("Lỗi cập nhật role user {}: {}", userId, e.getMessage());
            return ApiResponse.error("Cập nhật role thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> setUserEnabled(String userId, boolean enabled) {
        try {
            UserResource userResource = getUsersResource().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(enabled);
            userResource.update(user);
            log.info("Cập nhật trạng thái Keycloak user {} thành enabled={}", userId, enabled);
            return ApiResponse.success("Cập nhật trạng thái tài khoản thành công");
        } catch (Exception e) {
            log.error("Lỗi cập nhật trạng thái Keycloak user {}: {}", userId, e.getMessage());
            return ApiResponse.error("Cập nhật trạng thái tài khoản thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<Void> deleteUser(String userId) {
        try {
            UserResource userResource = getUsersResource().get(userId);
            userResource.remove();
            log.info("Xóa thành công user {} khỏi Keycloak", userId);
            return ApiResponse.success("Xóa tài khoản trên Keycloak thành công");
        } catch (Exception e) {
            log.error("Lỗi xóa user {} trên Keycloak: {}", userId, e.getMessage());
            return ApiResponse.error("Xóa tài khoản trên Keycloak thất bại", e.getMessage());
        }
    }

    @Override
    public ApiResponse<List<RoleResponse>> getAllRealmRoles() {
        try {
            List<RoleResponse> roles = getRealmResource().roles().list().stream()
                    .filter(role -> role.getName() != null && role.getName().startsWith("ROLE_"))
                    .map(role -> RoleResponse.builder()
                            .name(role.getName())
                            .description(role.getDescription())
                            .build())
                    .collect(Collectors.toList());
            return ApiResponse.success("Lấy danh sách roles thành công", roles);
        } catch (Exception e) {
            log.error("Lỗi lấy danh sách roles: {}", e.getMessage());
            return ApiResponse.error("Lấy danh sách roles thất bại", e.getMessage());
        }
    }

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
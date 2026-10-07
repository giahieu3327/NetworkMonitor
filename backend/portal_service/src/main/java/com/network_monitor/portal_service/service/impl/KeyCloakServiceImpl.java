package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;
import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.util.NameUtils;
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
        return getKeycloakInstance()
                .realm(realm);
    }

    @Override
    public UsersResource getUsersResource() {
        return getRealmResource()
                .users();
    }

    @Override
    public ApiResponse<Boolean> existsByUsername(
            String username
    ) {
        try {
            List<UserRepresentation> users =
                    getUsersResource()
                            .searchByUsername(username, true);

            return ApiResponse.success(
                    "Kiểm tra sự tồn tại của tên đăng nhập thành công",
                    !users.isEmpty()
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi kiểm tra username {}: {}",
                    username,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Kiểm tra tên đăng nhập thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<UserRepresentation> findByUsername(
            String username
    ) {
        try {
            List<UserRepresentation> users =
                    getUsersResource()
                            .searchByUsername(username, true);

            Optional<UserRepresentation> user =
                    users.stream()
                            .findFirst();

            return user
                    .map(userRepresentation ->
                            ApiResponse.success(
                                    "Lấy thông tin Keycloak user thành công",
                                    userRepresentation
                            )
                    )
                    .orElseGet(() ->
                            ApiResponse.error(
                                    "Không tìm thấy người dùng",
                                    "User "
                                            + username
                                            + " không tồn tại"
                            )
                    );

        } catch (Exception e) {
            log.error(
                    "Lỗi tìm kiếm user {}: {}",
                    username,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Tìm kiếm người dùng thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<UserRepresentation> findByEmail(
            String email
    ) {
        try {
            List<UserRepresentation> users =
                    getUsersResource()
                            .searchByEmail(email, true);

            Optional<UserRepresentation> user =
                    users.stream()
                            .filter(
                                    userRepresentation ->
                                            email.equalsIgnoreCase(
                                                    userRepresentation.getEmail()
                                            )
                            )
                            .findFirst();

            return user
                    .map(userRepresentation ->
                            ApiResponse.success(
                                    "Lấy thông tin Keycloak user theo email thành công",
                                    userRepresentation
                            )
                    )
                    .orElseGet(() ->
                            ApiResponse.error(
                                    "Không tìm thấy người dùng",
                                    "Email "
                                            + email
                                            + " không tồn tại"
                            )
                    );

        } catch (Exception e) {
            log.error(
                    "Lỗi tìm kiếm user theo email {}: {}",
                    email,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Tìm kiếm người dùng theo email thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<TokenResponse> login(
            String username,
            String password
    ) {
        String tokenUrl =
                serverUrl
                        + "/realms/"
                        + realm
                        + "/protocol/openid-connect/token";

        RestTemplate restTemplate =
                new RestTemplate();

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        MultiValueMap<String, String> map =
                new LinkedMultiValueMap<>();

        map.add(
                "grant_type",
                "password"
        );

        map.add(
                "client_id",
                userClientId
        );

        map.add(
                "username",
                username
        );

        map.add(
                "password",
                password
        );

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(
                        map,
                        headers
                );

        try {
            ResponseEntity<TokenResponse> response =
                    restTemplate.postForEntity(
                            tokenUrl,
                            request,
                            TokenResponse.class
                    );

            return ApiResponse.success(
                    "Đăng nhập thành công",
                    response.getBody()
            );

        } catch (Exception e) {
            log.error(
                    "Login failed for user {}: {}",
                    username,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Đăng nhập thất bại",
                    "Tên đăng nhập hoặc mật khẩu không chính xác!"
            );
        }
    }

    @Override
    public ApiResponse<TokenResponse> refreshToken(
            String refreshToken
    ) {
        String tokenUrl =
                serverUrl
                        + "/realms/"
                        + realm
                        + "/protocol/openid-connect/token";

        RestTemplate restTemplate =
                new RestTemplate();

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        MultiValueMap<String, String> map =
                new LinkedMultiValueMap<>();

        map.add(
                "grant_type",
                "refresh_token"
        );

        map.add(
                "client_id",
                userClientId
        );

        map.add(
                "refresh_token",
                refreshToken
        );

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(
                        map,
                        headers
                );

        try {
            ResponseEntity<TokenResponse> response =
                    restTemplate.postForEntity(
                            tokenUrl,
                            request,
                            TokenResponse.class
                    );

            return ApiResponse.success(
                    "Làm mới token thành công",
                    response.getBody()
            );

        } catch (Exception e) {
            log.error(
                    "Refresh token failed: {}",
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Refresh token thất bại",
                    "Refresh token không hợp lệ hoặc đã hết hạn!"
            );
        }
    }

    @Override
    public ApiResponse<Void> logout(
            String refreshToken
    ) {
        String logoutUrl =
                serverUrl
                        + "/realms/"
                        + realm
                        + "/protocol/openid-connect/logout";

        RestTemplate restTemplate =
                new RestTemplate();

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        MultiValueMap<String, String> map =
                new LinkedMultiValueMap<>();

        map.add(
                "client_id",
                userClientId
        );

        map.add(
                "refresh_token",
                refreshToken
        );

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(
                        map,
                        headers
                );

        try {
            restTemplate.postForEntity(
                    logoutUrl,
                    request,
                    String.class
            );

            return ApiResponse.success(
                    "Đăng xuất thành công"
            );

        } catch (Exception e) {
            log.error(
                    "Logout failed on Keycloak: {}",
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Đăng xuất thất bại",
                    "Refresh Token không hợp lệ!"
            );
        }
    }

    @Override
    public ApiResponse<String> createUser(
            String username,
            String email,
            String fullName
    ) {
        try {
            String[] nameParts =
                    NameUtils.splitFullName(fullName);

            String lastName =
                    nameParts[0];

            String firstName =
                    nameParts[1];

            UserRepresentation user =
                    new UserRepresentation();

            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(false);

            Response response =
                    getUsersResource()
                            .create(user);

            if (response.getStatus() != 201) {
                log.error(
                        "Failed to create user {} in Keycloak. Status: {}",
                        username,
                        response.getStatus()
                );

                return ApiResponse.error(
                        "Tạo user trên Keycloak thất bại",
                        "Mã trạng thái Keycloak: "
                                + response.getStatus()
                );
            }

            String path =
                    response.getLocation()
                            .getPath();

            String userId =
                    path.substring(
                            path.lastIndexOf('/') + 1
                    );

            return ApiResponse.success(
                    "Tạo user trên Keycloak thành công",
                    userId
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi tạo user {}: {}",
                    username,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Tạo user trên Keycloak thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<Void> setPassword(
            String userId,
            String password,
            boolean temporary
    ) {
        try {
            CredentialRepresentation credential =
                    new CredentialRepresentation();

            credential.setType(
                    CredentialRepresentation.PASSWORD
            );

            credential.setValue(password);
            credential.setTemporary(temporary);

            UserResource userResource =
                    getUsersResource()
                            .get(userId);

            userResource.resetPassword(
                    credential
            );

            return ApiResponse.success(
                    "Cấu hình mật khẩu thành công"
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi đặt mật khẩu user {}: {}",
                    userId,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Cấu hình mật khẩu thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<Void> setEmailVerified(
            String userId,
            boolean verified
    ) {
        try {
            UserResource userResource =
                    getUsersResource()
                            .get(userId);

            UserRepresentation user =
                    userResource.toRepresentation();

            user.setEmailVerified(verified);

            userResource.update(user);

            return ApiResponse.success(
                    verified
                            ? "Xác thực email trên Keycloak thành công"
                            : "Hủy xác thực email trên Keycloak thành công"
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi cập nhật emailVerified user {}: {}",
                    userId,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Cập nhật trạng thái xác thực email trên Keycloak thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<Void> assignRealmRole(
            String userId,
            String roleName
    ) {
        try {
            RoleRepresentation role =
                    getRealmResource()
                            .roles()
                            .get(roleName)
                            .toRepresentation();

            UserResource userResource =
                    getUsersResource()
                            .get(userId);

            userResource
                    .roles()
                    .realmLevel()
                    .add(
                            Collections.singletonList(role)
                    );

            return ApiResponse.success(
                    "Gán role "
                            + roleName
                            + " thành công"
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi gán role cho user {}: {}",
                    userId,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Gán role thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<Void> updateUser(
            String userId,
            String username,
            String email,
            String fullName
    ) {
        try {
            UserResource userResource =
                    getUsersResource()
                            .get(userId);

            UserRepresentation user =
                    userResource.toRepresentation();

            // Cập nhật Username nếu có thay đổi
            if (username != null
                    && !username.isBlank()
                    && !username.equals(user.getUsername())) {
                user.setUsername(username);
            }

            // Cập nhật Email nếu có thay đổi
            if (email != null
                    && !email.isBlank()
                    && !email.equalsIgnoreCase(
                            user.getEmail()
                    )) {

                user.setEmail(email);
                user.setEmailVerified(false);
            }

            // Cập nhật FullName nếu có thay đổi
            if (fullName != null
                    && !fullName.isBlank()) {

                String[] nameParts =
                        NameUtils.splitFullName(fullName);

                user.setFirstName(
                        nameParts[1]
                );

                user.setLastName(
                        nameParts[0]
                );
            }

            userResource.update(user);

            return ApiResponse.success(
                    "Cập nhật thông tin Keycloak user thành công"
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi cập nhật Keycloak user {}: {}",
                    userId,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Cập nhật thông tin trên Keycloak thất bại",
                    e.getMessage()
            );
        }
    }
    
    @Override
    public ApiResponse<Void> updateUserRole(
            String userId,
            String roleName
    ) {
        try {
            UserResource userResource =
                    getUsersResource()
                            .get(userId);

            List<RoleRepresentation> currentRoles =
                    userResource
                            .roles()
                            .realmLevel()
                            .listAll();

            if (!currentRoles.isEmpty()) {
                userResource
                        .roles()
                        .realmLevel()
                        .remove(currentRoles);
            }

            RoleRepresentation newRole =
                    getRealmResource()
                            .roles()
                            .get(roleName)
                            .toRepresentation();

            userResource
                    .roles()
                    .realmLevel()
                    .add(
                            Collections.singletonList(newRole)
                    );

            return ApiResponse.success(
                    "Cập nhật role thành công"
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi cập nhật role user {}: {}",
                    userId,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Cập nhật role thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<Void> setUserEnabled(
            String userId,
            boolean enabled
    ) {
        try {
            UserResource userResource =
                    getUsersResource()
                            .get(userId);

            UserRepresentation user =
                    userResource.toRepresentation();

            user.setEnabled(enabled);

            userResource.update(user);

            return ApiResponse.success(
                    "Cập nhật trạng thái tài khoản thành công"
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi cập nhật trạng thái user {}: {}",
                    userId,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Cập nhật trạng thái tài khoản thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<Void> deleteUser(
            String userId
    ) {
        try {
            UserResource userResource =
                    getUsersResource()
                            .get(userId);

            userResource.remove();

            return ApiResponse.success(
                    "Xóa tài khoản trên Keycloak thành công"
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi xóa user {} trên Keycloak: {}",
                    userId,
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Xóa tài khoản trên Keycloak thất bại",
                    e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<List<RoleResponse>> getAllRealmRoles() {
        try {
            List<RoleResponse> roles =
                    getRealmResource()
                            .roles()
                            .list()
                            .stream()
                            .filter(
                                    role ->
                                            role.getName() != null
                                                    && role.getName()
                                                    .startsWith("ROLE_")
                            )
                            .map(
                                    role ->
                                            RoleResponse.builder()
                                                    .name(role.getName())
                                                    .description(
                                                            role.getDescription()
                                                    )
                                                    .build()
                            )
                            .collect(Collectors.toList());

            return ApiResponse.success(
                    "Lấy danh sách roles thành công",
                    roles
            );

        } catch (Exception e) {
            log.error(
                    "Lỗi lấy danh sách roles: {}",
                    e.getMessage()
            );

            return ApiResponse.error(
                    "Lấy danh sách roles thất bại",
                    e.getMessage()
            );
        }
    }
}

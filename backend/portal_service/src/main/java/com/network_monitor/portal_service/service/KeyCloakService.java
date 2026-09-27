package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.RoleResponse;
import com.network_monitor.portal_service.model.dto.response.TokenResponse;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

public interface KeyCloakService {

    // --- QUẢN LÝ TÀI NGUYÊN KEYCLOAK CLIENT ---
    Keycloak getKeycloakInstance();
    RealmResource getRealmResource();
    UsersResource getUsersResource();

    // --- TRUY VẤN VÀ KIỂM TRA THÔNG TIN ---
    ApiResponse<Boolean> existsByUsername(String username);
    ApiResponse<UserRepresentation> findByUsername(String username);
    ApiResponse<String> findUserIdByUsername(String username);

    // --- AUTHENTICATION & SESSION ---
    ApiResponse<TokenResponse> login(String username, String password);
    ApiResponse<TokenResponse> refreshToken(String refreshToken);
    ApiResponse<Void> logout(String refreshToken);

    // --- TẠO VÀ CẤU HÌNH TÀI KHOẢN ---
    ApiResponse<String> createUser(String username, String email, String fullName);
    ApiResponse<Void> setPassword(String userId, String password, boolean temporary);
    ApiResponse<Void> assignRealmRole(String userId, String roleName);
    ApiResponse<String> createAndConfigureUser(String username, String email, String fullName, String password, String roleName, boolean sendVerificationEmail);

    // --- EMAIL VERIFICATION & ACTIONS (GIỮ NGUYÊN HÀM NÀY) ---
    ApiResponse<Void> sendVerificationEmail(String userId);
    ApiResponse<Void> sendResetPasswordEmail(String userId);

    // --- CẬP NHẬT & VẬN HÀNH TÀI KHOẢN ---
    ApiResponse<Void> changePassword(String userId, String username, String currentPassword, String newPassword);
    ApiResponse<Void> updateKeycloakUser(String userId, String email, String fullName);
    ApiResponse<Void> updateUserRole(String userId, String newRoleName);
    ApiResponse<Void> setUserEnabled(String userId, boolean enabled);
    ApiResponse<Void> deleteUser(String userId);

    // --- PHÂN QUYỀN ROLES ---
    ApiResponse<List<RoleResponse>> getAllRealmRoles();
}
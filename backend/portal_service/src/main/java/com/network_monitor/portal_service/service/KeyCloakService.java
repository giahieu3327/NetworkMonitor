package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

public interface KeyCloakService {

    Keycloak getKeycloakInstance();

    RealmResource getRealmResource();

    UsersResource getUsersResource();

    ApiResponse<Boolean> existsByUsername(String username);

    ApiResponse<UserRepresentation> findByUsername(String username);

    ApiResponse<UserRepresentation> findByEmail(String email);

    ApiResponse<TokenResponse> login(
            String username,
            String password
    );

    ApiResponse<TokenResponse> refreshToken(
            String refreshToken
    );

    ApiResponse<Void> logout(
            String refreshToken
    );

    ApiResponse<String> createUser(
            String username,
            String email,
            String fullName
    );

    ApiResponse<Void> setPassword(
            String userId,
            String password,
            boolean temporary
    );

    ApiResponse<Void> setEmailVerified(
            String userId,
            boolean verified
    );

    ApiResponse<Void> assignRealmRole(
            String userId,
            String roleName
    );

    ApiResponse<Void> updateUser(
            String userId,
            String username,
            String email,
            String fullName
    );

    ApiResponse<Void> updateUserRole(
            String userId,
            String roleName
    );

    ApiResponse<Void> setUserEnabled(
            String userId,
            boolean enabled
    );

    ApiResponse<Void> deleteUser(
            String userId
    );

    ApiResponse<List<RoleResponse>> getAllRealmRoles();
}
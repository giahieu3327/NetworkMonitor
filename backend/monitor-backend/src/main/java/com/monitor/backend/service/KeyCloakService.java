package com.monitor.backend.service;

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
import org.springframework.stereotype.Service;

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

    @Value("${keycloak.admin.password:Admin@123}")
    private String adminPassword;

    @Value("${keycloak.admin.client-id:admin-cli}")
    private String adminClientId;

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

    public String createUser(String username, String email, String fullName) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(fullName);
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
}

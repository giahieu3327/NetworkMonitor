package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(
            name = "id",
            length = 36,
            nullable = false
    )
    private String id; // Keycloak Subject ID (UUID)

    @Column(
            name = "username",
            length = 100,
            nullable = false,
            unique = true
    )
    private String username; // Keycloak username

    @Column(
            name = "email",
            length = 255,
            nullable = false,
            unique = true
    )
    private String email; // Keycloak email

    @Column(
            name = "full_name",
            length = 255,
            nullable = false
    )
    private String fullName; // Keycloak firstName + lastName

    @Column(
            name = "phone_number",
            length = 30
    )
    private String phoneNumber; // Keycloak attribute

    @Column(
            name = "role_name",
            length = 100,
            nullable = false
    )
    private String roleName; // Keycloak role

    @Column(
            name = "is_active",
            nullable = false
    )
    @Builder.Default
    private Boolean isActive = true; // Keycloak enabled

    @Column(
            name = "email_verified",
            nullable = false
    )
    @Builder.Default
    private Boolean emailVerified = false; // Keycloak emailVerified

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // Keycloak createdTimestamp

    @Column(
            name = "updated_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(); // PostgreSQL quản lý
}
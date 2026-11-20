package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    // ID người dùng đồng bộ từ Keycloak.
    @Id
    @Column(
        name = "id",
        length = 36,
        nullable = false
    )
    private String id;

    @Column(
        name = "username",
        length = 100,
        nullable = false,
        unique = true
    )
    private String username;

    @Column(
        name = "email",
        length = 255,
        nullable = false,
        unique = true
    )
    private String email;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // Ví dụ: ADMIN, MANAGER, GUEST_VIEWER.
    @Builder.Default
    @Column(
        name = "role_name",
        length = 50,
        nullable = false
    )
    private String roleName = "GUEST_VIEWER";

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Builder.Default
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
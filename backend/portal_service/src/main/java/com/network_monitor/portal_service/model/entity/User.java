package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id; // Keycloak UUID

    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "role_name", length = 50)
    @Builder.Default
    private String roleName = "ROLE_GUEST_VIEWER";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    // Tên đăng nhập của người dùng.
    @Column(
            name = "username",
            length = 100,
            nullable = false,
            unique = true
    )
    private String username;

    // Email người dùng.
    @Column(
            name = "email",
            length = 255,
            nullable = false,
            unique = true
    )
    private String email;

    // Họ và tên đầy đủ.
    @Column(
            name = "full_name",
            length = 150
    )
    private String fullName;

    // Số điện thoại liên hệ.
    @Column(
            name = "phone_number",
            length = 20
    )
    private String phoneNumber;

    // Vai trò trong ứng dụng, ví dụ ADMIN, MANAGER.
    // Dùng String, không sử dụng Enum.
    @Column(
            name = "role_name",
            length = 50,
            nullable = false
    )
    private String roleName;

    // Trạng thái hoạt động của tài khoản.
    @Builder.Default
    @Column(
            name = "is_active",
            nullable = false
    )
    private Boolean isActive = true;

    // Trạng thái xác minh email.
    @Builder.Default
    @Column(
            name = "email_verified",
            nullable = false
    )
    private Boolean emailVerified = false;

    // Thời điểm tạo bản ghi.
    // PostgreSQL có DEFAULT CURRENT_TIMESTAMP.
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Thời điểm cập nhật gần nhất.
    // PostgreSQL trigger tự cập nhật khi UPDATE.
    @Column(
            name = "updated_at",
            nullable = false
    )
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}

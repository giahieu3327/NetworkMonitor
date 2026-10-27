package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    // ID thiết bị, tự động tăng bằng BIGSERIAL.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Tên thiết bị, không được trùng.
    @Column(
            name = "device_name",
            length = 150,
            nullable = false,
            unique = true
    )
    private String deviceName;

// Địa chỉ IP, tương ứng PostgreSQL INET.
@ColumnTransformer(
        read = "host(ip_address)",
        write = "?::inet"
)
@Column(
        name = "ip_address",
        columnDefinition = "inet",
        nullable = false,
        unique = true
)
private String ipAddress;

    // Loại thiết bị, ví dụ ROUTER, SWITCH, FIREWALL.
    @Column(
            name = "device_type",
            length = 50,
            nullable = false
    )
    private String deviceType;

    // Trạng thái thiết bị: UP, DOWN hoặc UNKNOWN.
    @Builder.Default
    @Column(
            name = "status",
            length = 20,
            nullable = false
    )
    private String status = "UNKNOWN";

    // Thời điểm tạo bản ghi.
    // PostgreSQL DEFAULT CURRENT_TIMESTAMP tự thiết lập giá trị.
    @Builder.Default
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Thời điểm cập nhật gần nhất.
    // Trigger PostgreSQL tự cập nhật khi UPDATE.
    @Builder.Default
    @Column(
            name = "updated_at",
            nullable = false
    )
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
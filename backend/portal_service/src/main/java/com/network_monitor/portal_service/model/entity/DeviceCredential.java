package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceCredential {

    // ID thông tin xác thực, tự động tăng bằng BIGSERIAL.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Thiết bị sở hữu thông tin xác thực.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    // Tên mô tả thông tin xác thực.
    @Column(
            name = "credential_name",
            length = 100,
            nullable = false
    )
    private String credentialName;

    // Loại thông tin xác thực: SNMP hoặc SYSLOG.
    @Column(
            name = "credential_type",
            length = 20,
            nullable = false
    )
    private String credentialType;

    // Giao thức: SNMPV1, SNMPV2C, SNMPV3, UDP, TCP hoặc TLS.
    @Column(
            name = "protocol_type",
            length = 20,
            nullable = false
    )
    private String protocolType;

    // Community string dùng cho SNMP v1/v2c.
    @Column(
            name = "community_string",
            columnDefinition = "text"
    )
    private String communityString;

    // Cổng kết nối; có thể NULL theo schema.
    @Column(name = "port")
    private Integer port;

    // Trạng thái kích hoạt thông tin xác thực.
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Thời điểm tạo bản ghi.
    // PostgreSQL DEFAULT CURRENT_TIMESTAMP tự thiết lập.
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
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
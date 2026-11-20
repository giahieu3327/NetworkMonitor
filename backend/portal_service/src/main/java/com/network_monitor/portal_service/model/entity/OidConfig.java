package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "oid_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OidConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // DEVICE, INTERFACE, METRIC hoặc ARP.
    @Builder.Default
    @Column(
        name = "metric_scope",
        length = 20,
        nullable = false
    )
    private String metricScope = "DEVICE";

    @Column(
        name = "metric_type",
        length = 100,
        nullable = false
    )
    private String metricType;

    @Column(
        name = "oid_pattern",
        columnDefinition = "text",
        nullable = false
    )
    private String oidPattern;

    @Column(name = "oid_kind", nullable = false, length = 20)
    private String oidKind;

    // INTEGER, COUNTER, GAUGE, TIMETICKS...
    @Builder.Default
    @Column(
        name = "data_type",
        length = 30,
        nullable = false
    )
    private String dataType = "INTEGER";

    @Builder.Default
    @Column(name = "multiplier", nullable = false)
    private Double multiplier = 1.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    // NULL nếu cấu hình không gắn với interface cụ thể.
    @Column(name = "interface_index")
    private Integer interfaceIndex;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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

    // Quan hệ chỉ đọc tới interface.
    // device và interfaceIndex là hai trường quản lý giá trị khóa ngoại.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(
            name = "device_id",
            referencedColumnName = "device_id",
            insertable = false,
            updatable = false
        ),
        @JoinColumn(
            name = "interface_index",
            referencedColumnName = "interface_index",
            insertable = false,
            updatable = false
        )
    })
    private DeviceInterface deviceInterface;
}
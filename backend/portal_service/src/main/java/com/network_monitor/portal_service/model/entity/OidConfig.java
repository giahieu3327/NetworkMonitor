package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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
    private Long id;

    @Builder.Default
    @Column(
            name = "metric_scope",
            length = 100,
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
            length = 500,
            nullable = false
    )
    private String oidPattern;

    @Builder.Default
    @Column(
            name = "data_type",
            length = 100,
            nullable = false
    )
    private String dataType = "INTEGER";

    @Builder.Default
    @Column(
            name = "multiplier",
            nullable = false
    )
    private Double multiplier = 1.0;

    @Column(
            name = "device_type",
            length = 100
    )
    private String deviceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id"
    )
    private Device device;

    @Column(
            name = "interface_index"
    )
    private Integer interfaceIndex;

    @Builder.Default
    @Column(
            name = "is_active",
            nullable = false
    )
    private Boolean isActive = true;

    @Column(
            name = "description",
            columnDefinition = "TEXT"
    )
    private String description;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(
            name = "updated_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns({
        @JoinColumn(name = "device_id", referencedColumnName = "device_id", insertable = false, updatable = false),
        @JoinColumn(name = "interface_index", referencedColumnName = "interface_index", insertable = false, updatable = false)
        })
        private DeviceInterface deviceInterface;
}
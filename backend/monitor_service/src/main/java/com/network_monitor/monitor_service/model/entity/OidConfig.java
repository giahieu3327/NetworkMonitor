package com.network_monitor.monitor_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "oid_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OidConfig {

    @Id
    @Column(
            name = "id",
            nullable = false
    )
    private Integer id;

    @Column(
            name = "metric_scope",
            nullable = false
    )
    private String metricScope;

    @Column(
            name = "metric_type",
            nullable = false
    )
    private String metricType;

    @Column(
            name = "oid_pattern",
            nullable = false
    )
    private String oidPattern;

    @Column(
            name = "data_type",
            nullable = false
    )
    private String dataType;

    @Builder.Default
    @Column(
            name = "multiplier",
            nullable = false
    )
    private Double multiplier = 1.0;

    @Column(name = "device_type")
    private String deviceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "interface_index")
    private Integer interfaceIndex;

    @Builder.Default
    @Column(
            name = "is_active",
            nullable = false
    )
    private Integer isActive = 1;

    @Column(name = "description")
    private String description;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private String createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private String updatedAt;
}
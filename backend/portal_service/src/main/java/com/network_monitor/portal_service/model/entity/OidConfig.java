package com.network_monitor.portal_service.model.entity;

import com.network_monitor.portal_service.model.enums.MetricScope;
import com.network_monitor.portal_service.model.enums.OidDataType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "oid_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OidConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_scope", length = 20, nullable = false)
    private MetricScope metricScope = MetricScope.DEVICE;

    @Column(name = "metric_type", length = 30, nullable = false)
    private String metricType;

    @Column(name = "oid_pattern", nullable = false)
    private String oidPattern;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", length = 20)
    private OidDataType dataType = OidDataType.INTEGER;

    @Builder.Default
    @Column(precision = 53)
    private Double multiplier = 1.0;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interface_id")
    private DeviceInterface deviceInterface;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "threshold_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThresholdRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(
        name = "rule_name",
        length = 150,
        nullable = false
    )
    private String ruleName;

    @Column(
        name = "metric_type",
        length = 100,
        nullable = false
    )
    private String metricType;

    // Ngưỡng cảnh báo.
    @Column(name = "warning_limit")
    private Double warningLimit;

    // Ngưỡng nghiêm trọng.
    @Column(name = "critical_limit")
    private Double criticalLimit;

    @Builder.Default
    @Column(name = "consecutive_occurrences", nullable = false)
    private Integer consecutiveOccurrences = 3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    // NULL nếu áp dụng cho toàn bộ thiết bị.
    @Column(name = "interface_index")
    private Integer interfaceIndex;

    // Quan hệ chỉ đọc tới interface tương ứng.
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

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

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
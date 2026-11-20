package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "threshold_rules",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_threshold_rules_device_metric",
            columnNames = {"device_id", "metric_type"}
        )
    }
)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "metric_type", length = 100, nullable = false)
    private String metricType;

    @Column(name = "warning_limit")
    private Double warningLimit;

    @Column(name = "critical_limit")
    private Double criticalLimit;

    @Builder.Default
    @Column(name = "consecutive_occurrences", nullable = false)
    private Integer consecutiveOccurrences = 3;

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
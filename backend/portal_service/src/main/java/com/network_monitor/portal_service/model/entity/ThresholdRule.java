package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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
    private Long id;

    @Column(
            name = "rule_name",
            length = 255,
            nullable = false,
            unique = true
    )
    private String ruleName;

    @Column(
            name = "metric_type",
            length = 100,
            nullable = false
    )
    private String metricType;

    @Column(name = "warning_limit")
    private Double warningLimit;

    @Column(name = "critical_limit")
    private Double criticalLimit;

    @Builder.Default
    @Column(
            name = "consecutive_occurrences",
            nullable = false
    )
    private Integer consecutiveOccurrences = 3;

    @Builder.Default
    @Column(
            name = "duration_seconds",
            nullable = false
    )
    private Integer durationSeconds = 300;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "interface_index")
    private Integer interfaceIndex;

    @Builder.Default
    @Column(
            name = "is_enabled",
            nullable = false
    )
    private Boolean isEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

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
}
package com.network_monitor.portal_service.model.entity;

import com.network_monitor.portal_service.model.enums.MetricType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "threshold_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ThresholdRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", length = 100, nullable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", length = 30, nullable = false)
    private MetricType metricType;

    @Column(name = "warning_limit", nullable = false)
    private Double warningLimit;

    @Column(name = "critical_limit", nullable = false)
    private Double criticalLimit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Builder.Default
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

package com.network_monitor.portal_service.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "metric_type", length = 100, nullable = false)
    private String metricType;

    @Column(name = "severity", length = 20, nullable = false)
    private String severity;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "peak_value", nullable = false)
    private Double peakValue;

    @Column(name = "threshold_limit", nullable = false)
    private Double thresholdLimit;

    @Column(name = "breach_count", nullable = false)
    private Integer breachCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "breach_samples", columnDefinition = "jsonb")
    private JsonNode breachSamples;

    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "OPEN";

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
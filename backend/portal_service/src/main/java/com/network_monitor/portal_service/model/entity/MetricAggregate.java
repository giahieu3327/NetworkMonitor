package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "metric_aggregate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    // 1H, 6H hoặc 1D.
    @Column(
        name = "aggregation_window",
        length = 10,
        nullable = false
    )
    private String aggregationWindow;

    @Builder.Default
    @Column(name = "period_start", nullable = false)
    private OffsetDateTime periodStart = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "period_end", nullable = false)
    private OffsetDateTime periodEnd = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        name = "aggregated_data",
        columnDefinition = "jsonb",
        nullable = false
    )
    private Map<String, Object> aggregatedData;
}
package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false
    )
    private Device device;

    @Column(
            name = "interface_index"
    )
    private Integer interfaceIndex;

    @Column(
            name = "aggregation_window",
            nullable = false,
            length = 100
    )
    private String aggregationWindow;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "metrics",
            columnDefinition = "jsonb",
            nullable = false
    )
    private Map<String, Object> metrics;

    @Column(
            name = "period_start",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime periodStart = LocalDateTime.now();

    @Column(
            name = "period_end",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime periodEnd = LocalDateTime.now();
}
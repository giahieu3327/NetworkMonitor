package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    // Dữ liệu metric được lưu dưới dạng PostgreSQL JSONB.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        name = "metric_data",
        columnDefinition = "jsonb",
        nullable = false
    )
    private Map<String, Object> metricData;

    @Builder.Default
    @Column(
        name = "timestamp",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime timestamp = OffsetDateTime.now();
}
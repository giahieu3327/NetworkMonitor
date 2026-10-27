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

    // ID bản ghi tổng hợp, tự động tăng bằng BIGSERIAL.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Thiết bị có dữ liệu được tổng hợp.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false
    )
    private Device device;

    // Khoảng tổng hợp: 1H, 6H hoặc 1D.
    @Column(
            name = "aggregation_window",
            length = 10,
            nullable = false
    )
    private String aggregationWindow;

    // Dữ liệu tổng hợp lưu dưới dạng JSONB.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "aggregated_data",
            columnDefinition = "jsonb",
            nullable = false
    )
    private Map<String, Object> aggregatedData;

    // Thời điểm bắt đầu khoảng tổng hợp.
    @Builder.Default
    @Column(
            name = "period_start",
            nullable = false
    )
    private OffsetDateTime periodStart = OffsetDateTime.now();

    // Thời điểm kết thúc khoảng tổng hợp.
    @Builder.Default
    @Column(
            name = "period_end",
            nullable = false
    )
    private OffsetDateTime periodEnd = OffsetDateTime.now();
}
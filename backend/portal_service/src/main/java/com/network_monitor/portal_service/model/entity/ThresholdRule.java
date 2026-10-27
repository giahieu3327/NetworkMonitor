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

    // ID quy tắc ngưỡng, tự động tăng bằng BIGSERIAL.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Tên quy tắc.
    @Column(
            name = "rule_name",
            length = 150,
            nullable = false
    )
    private String ruleName;

    // Loại metric được giám sát.
    @Column(
            name = "metric_type",
            length = 100,
            nullable = false
    )
    private String metricType;

    // Ngưỡng cảnh báo; có thể NULL.
    @Column(name = "warning_limit")
    private Double warningLimit;

    // Ngưỡng nghiêm trọng; có thể NULL.
    @Column(name = "critical_limit")
    private Double criticalLimit;

    // Số lần liên tiếp vượt ngưỡng trước khi kích hoạt.
    @Builder.Default
    @Column(
            name = "consecutive_occurrences",
            nullable = false
    )
    private Integer consecutiveOccurrences = 3;

    // Thiết bị áp dụng quy tắc.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    // Interface áp dụng quy tắc; NULL nếu áp dụng cấp thiết bị.
    @Column(name = "interface_index")
    private Integer interfaceIndex;

    // Trạng thái bật/tắt quy tắc.
    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    // Thời điểm tạo bản ghi.
    // PostgreSQL DEFAULT CURRENT_TIMESTAMP tự thiết lập.
    @Builder.Default
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Thời điểm cập nhật gần nhất.
    // Trigger PostgreSQL tự cập nhật khi UPDATE.
    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
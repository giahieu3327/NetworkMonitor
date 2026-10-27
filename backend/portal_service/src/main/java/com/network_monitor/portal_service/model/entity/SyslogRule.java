package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "syslog_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyslogRule {

    // ID quy tắc syslog, tự động tăng bằng BIGSERIAL.
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

    // Mức độ nghiêm trọng cần khớp; có thể NULL.
    @Column(
            name = "match_severity",
            length = 20
    )
    private String matchSeverity;

    // Mẫu dùng để so khớp nội dung syslog.
    @Column(
            name = "match_pattern",
            columnDefinition = "text"
    )
    private String matchPattern;

    // Mức độ nghiêm trọng gán cho incident.
    // Các giá trị theo schema: INFO, WARNING, CRITICAL.
    @Builder.Default
    @Column(
            name = "assign_severity",
            length = 20,
            nullable = false
    )
    private String assignSeverity = "CRITICAL";

    // Thiết bị áp dụng quy tắc; NULL nếu áp dụng toàn hệ thống.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

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
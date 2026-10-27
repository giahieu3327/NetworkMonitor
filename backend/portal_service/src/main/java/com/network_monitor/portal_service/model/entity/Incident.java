package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    // ID incident, tự động tăng bằng BIGSERIAL.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Thiết bị liên quan; có thể NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    // Quy tắc ngưỡng gây ra incident; có thể NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "threshold_rule_id")
    private ThresholdRule thresholdRule;

    // Quy tắc syslog gây ra incident; có thể NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syslog_rule_id")
    private SyslogRule syslogRule;

    // Bản ghi syslog liên quan; có thể NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syslog_id")
    private Syslog syslog;

    // Mức độ nghiêm trọng: INFO, WARNING hoặc CRITICAL.
    @Column(
            name = "severity",
            length = 20,
            nullable = false
    )
    private String severity;

    // Tiêu đề incident.
    @Column(
            name = "title",
            length = 200,
            nullable = false
    )
    private String title;

    // Nội dung mô tả incident.
    @Column(
            name = "description",
            columnDefinition = "text",
            nullable = false
    )
    private String description;

    // ID tiến trình Camunda liên quan; có thể NULL.
    @Column(
            name = "camunda_process_id",
            length = 100
    )
    private String camundaProcessId;

    // Trạng thái: OPEN, ACKNOWLEDGED hoặc RESOLVED.
    @Builder.Default
    @Column(
            name = "status",
            length = 30,
            nullable = false
    )
    private String status = "OPEN";

    // Thời điểm tạo incident.
    // PostgreSQL DEFAULT CURRENT_TIMESTAMP tự thiết lập.
    @Builder.Default
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Thời điểm xử lý xong; NULL nếu chưa giải quyết.
    @Builder.Default
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt = OffsetDateTime.now();
}
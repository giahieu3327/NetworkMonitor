package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private ThresholdRule thresholdRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syslog_rule_id")
    private SyslogRule syslogRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syslog_id")
    private Syslog syslog;

    @Column(
            name = "severity",
            length = 100,
            nullable = false
    )
    private String severity;

    @Column(
            name = "title",
            length = 500,
            nullable = false
    )
    private String title;

    @Column(
            name = "message",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String message;

    @Column(
            name = "camunda_process_id",
            length = 255
    )
    private String camundaProcessId;

    @Builder.Default
    @Column(
            name = "status",
            length = 100,
            nullable = false
    )
    private String status = "OPEN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    @Builder.Default
    private LocalDateTime acknowledgedAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    @Builder.Default
    private LocalDateTime resolvedAt = LocalDateTime.now();

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
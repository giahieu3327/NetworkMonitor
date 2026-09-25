package com.network_monitor.portal_service.model.entity;

import com.network_monitor.portal_service.model.enums.IncidentSeverity;
import com.network_monitor.portal_service.model.enums.IncidentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "incidents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
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

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private IncidentSeverity severity;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "camunda_process_id", length = 64)
    private String camundaProcessId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private IncidentStatus status = IncidentStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
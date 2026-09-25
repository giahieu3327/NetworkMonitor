package com.network_monitor.portal_service.model.entity;

import com.network_monitor.portal_service.model.enums.IncidentSeverity;
import com.network_monitor.portal_service.model.enums.SyslogSeverity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "syslog_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SyslogRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", length = 100, nullable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_severity", length = 20)
    private SyslogSeverity matchSeverity;

    @Column(name = "match_pattern")
    private String matchPattern;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "assign_severity", length = 20, nullable = false)
    private IncidentSeverity assignSeverity = IncidentSeverity.CRITICAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Builder.Default
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "syslog_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyslogRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "rule_name",
            length = 255,
            nullable = false,
            unique = true
    )
    private String ruleName;

    @Column(
            name = "match_severity",
            length = 100
    )
    private String matchSeverity;

    @Column(
            name = "match_pattern",
            columnDefinition = "TEXT"
    )
    private String matchPattern;

    @Builder.Default
    @Column(
            name = "assign_severity",
            length = 100,
            nullable = false
    )
    private String assignSeverity = "CRITICAL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Builder.Default
    @Column(
            name = "is_enabled",
            nullable = false
    )
    private Boolean isEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

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
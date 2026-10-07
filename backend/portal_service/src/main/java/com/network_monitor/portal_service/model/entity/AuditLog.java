package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(
            name = "action",
            length = 255,
            nullable = false
    )
    private String action;

    @Column(
            name = "module",
            length = 255,
            nullable = false
    )
    private String module;

    @Column(
            name = "ip_address",
            length = 45
    )
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "details",
            columnDefinition = "jsonb",
            nullable = false
    )
    private Map<String, Object> details;

    @Builder.Default
    @Column(
            name = "timestamp",
            nullable = false
    )
    private LocalDateTime timestamp = LocalDateTime.now();
}
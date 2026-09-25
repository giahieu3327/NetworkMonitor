package com.network_monitor.portal_service.model.entity;

import com.network_monitor.portal_service.model.enums.SyslogSeverity;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "syslogs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Syslog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;

    @Column(length = 20)
    private String facility;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SyslogSeverity severity;

    @Column(name = "app_name", length = 50)
    private String appName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "raw_log", columnDefinition = "TEXT")
    private String rawLog;

    @Builder.Default
    @Column(nullable = false)
    private OffsetDateTime timestamp = OffsetDateTime.now();
}
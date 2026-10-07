package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "syslogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Syslog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(
            name = "ip_address",
            length = 45,
            nullable = false
    )
    private String ipAddress;

    @Column(
            name = "facility",
            length = 100,
            nullable = false
    )
    private String facility;

    @Column(
            name = "severity",
            length = 100,
            nullable = false
    )
    private String severity;

    @Column(
            name = "app_name",
            length = 255
    )
    private String appName;

    @Column(
            name = "message",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String message;

    @Column(
            name = "raw_log",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String rawLog;

    @Builder.Default
    @Column(
            name = "timestamp",
            nullable = false
    )
    private LocalDateTime timestamp = LocalDateTime.now();
}
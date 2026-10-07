package com.network_monitor.monitor_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

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
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(
            name = "ip_address",
            nullable = false
    )
    private String ipAddress;

    @Column(
            name = "facility",
            nullable = false
    )
    private String facility;

    @Column(
            name = "severity",
            nullable = false
    )
    private String severity;

    @Column(name = "app_name")
    private String appName;

    @Column(
            name = "message",
            nullable = false
    )
    private String message;

    @Column(
            name = "raw_log",
            nullable = false
    )
    private String rawLog;

    @Builder.Default
    @Column(
            name = "timestamp",
            nullable = false
    )
    private String timestamp = java.time.LocalDateTime.now().toString();
}
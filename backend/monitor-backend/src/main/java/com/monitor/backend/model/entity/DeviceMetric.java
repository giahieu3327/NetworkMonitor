package com.monitor.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_metrics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "cpu_usage_pct")
    private Double cpuUsagePct;

    @Column(name = "ram_usage_pct")
    private Double ramUsagePct;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "active_sessions")
    private Integer activeSessions;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "latency_ms")
    private Double latencyMs;

    @Column(name = "packet_loss_pct")
    private Double packetLossPct;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private OffsetDateTime timestamp;
}

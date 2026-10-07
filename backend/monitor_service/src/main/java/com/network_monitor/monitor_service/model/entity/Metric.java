package com.network_monitor.monitor_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false
    )
    private Device device;

    @Column(name = "interface_index")
    private Integer interfaceIndex;

    @Column(
            name = "metrics",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String metrics;

    @Builder.Default
    @Column(
            name = "timestamp",
            nullable = false
    )
    private String timestamp = java.time.LocalDateTime.now().toString();
}
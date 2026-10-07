package com.network_monitor.monitor_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "device_name", nullable = false, unique = true)
    private String deviceName;

    @Column(name = "ip_address", nullable = false, unique = true)
    private String ipAddress;

    @Column(name = "device_type", nullable = false)
    private String deviceType;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "firmware_version", nullable = false)
    private String firmwareVersion;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;
}
package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "device_name",
            length = 255,
            nullable = false,
            unique = true
    )
    private String deviceName;

    @Column(
            name = "ip_address",
            length = 45,
            nullable = false,
            unique = true
    )
    private String ipAddress;

    @Column(
            name = "device_type",
            length = 100,
            nullable = false
    )
    private String deviceType;

    @Column(
            name = "model",
            length = 255,
            nullable = false
    )
    private String model;

    @Column(
            name = "firmware_version",
            length = 255,
            nullable = false
    )
    private String firmwareVersion;

    @Builder.Default
    @Column(
            name = "status",
            length = 100,
            nullable = false
    )
    private String status = "UP";

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

    @OneToMany(
            mappedBy = "device",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<DeviceCredential> credentials;

    @OneToMany(
            mappedBy = "device",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<DeviceInterface> interfaces;
}
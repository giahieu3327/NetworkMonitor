package com.network_monitor.portal_service.model.entity;

import com.network_monitor.portal_service.model.enums.DeviceStatus;
import com.network_monitor.portal_service.model.enums.DeviceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_name", length = 100, nullable = false)
    private String deviceName;

    @Column(name = "ip_address", length = 45, nullable = false, unique = true)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 30, nullable = false)
    private DeviceType deviceType;

    @Column(length = 100)
    private String model;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeviceStatus status = DeviceStatus.UP;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeviceCredential> credentials;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeviceInterface> interfaces;
}
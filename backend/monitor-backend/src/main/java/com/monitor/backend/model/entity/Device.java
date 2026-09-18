package com.monitor.backend.model.entity;

import com.monitor.backend.model.enums.DeviceStatus;
import com.monitor.backend.model.enums.DeviceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.UP;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}


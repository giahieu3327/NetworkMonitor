package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_interfaces")
@IdClass(DeviceInterface.DeviceInterfaceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceInterface {

    @Id
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Id
    @Column(name = "interface_index", nullable = false)
    private Integer interfaceIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Device device;

    @Column(
            name = "interface_name",
            length = 255,
            nullable = false
    )
    private String interfaceName;

    @Column(
            name = "mac_address",
            length = 50
    )
    private String macAddress;

    @Column(name = "speed_bps")
    private Long speedBps;

    @Column(
            name = "admin_status",
            length = 100,
            nullable = false
    )
    private String adminStatus;

    @Column(
            name = "oper_status",
            length = 100,
            nullable = false
    )
    private String operStatus;

    @CreationTimestamp
    @Column(
            name = "discovered_at",
            nullable = false,
            updatable = false
    )
    @Builder.Default
    private LocalDateTime discoveredAt = LocalDateTime.now();

    @Column(
            name = "updated_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();


    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class DeviceInterfaceId implements Serializable {

        private Long deviceId;

        private Integer interfaceIndex;
    }
}
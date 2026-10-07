package com.network_monitor.monitor_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

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
    @Column(
            name = "device_id",
            nullable = false
    )
    private Integer deviceId;

    @Id
    @Column(
            name = "interface_index",
            nullable = false
    )
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
            nullable = false
    )
    private String interfaceName;

    @Column(name = "mac_address")
    private String macAddress;

    @Column(name = "speed_bps")
    private Integer speedBps;

    @Column(
            name = "admin_status",
            nullable = false
    )
    private String adminStatus;

    @Column(
            name = "oper_status",
            nullable = false
    )
    private String operStatus;

    @Column(
            name = "discovered_at",
            nullable = false,
            updatable = false
    )
    private String discoveredAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private String updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class DeviceInterfaceId implements Serializable {

        private Integer deviceId;

        private Integer interfaceIndex;
    }
}
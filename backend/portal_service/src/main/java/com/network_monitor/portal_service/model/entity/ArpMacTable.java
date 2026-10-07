package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "arp_mac_tables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArpMacTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "ip_address",
            length = 45,
            nullable = false
    )
    private String ipAddress;

    @Column(
            name = "mac_address",
            length = 50,
            nullable = false
    )
    private String macAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false
    )
    private Device device;

    @Column(name = "interface_index")
    private Integer interfaceIndex;

    @Column(name = "vlan_id")
    private Integer vlanId;

    @Column(
            name = "last_seen",
            nullable = false
    )

    @Builder.Default
    private LocalDateTime lastSeen = LocalDateTime.now();

    @Column(
            name = "updated_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
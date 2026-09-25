package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "arp_mac_tables")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ArpMacTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;

    @Column(name = "mac_address", length = 17, nullable = false)
    private String macAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "interface_name", length = 50, nullable = false)
    private String interfaceName;

    @Column(name = "vlan_id")
    private Integer vlanId;

    @Builder.Default
    @Column(name = "last_seen")
    private OffsetDateTime lastSeen = OffsetDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
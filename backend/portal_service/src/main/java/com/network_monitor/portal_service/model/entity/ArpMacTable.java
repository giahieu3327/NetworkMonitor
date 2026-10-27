package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

import org.hibernate.annotations.ColumnTransformer;

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
    @Column(name = "id")
    private Long id;

    // Địa chỉ IP, tương ứng PostgreSQL INET.
@ColumnTransformer(
        read = "ip_address::text",
        write = "?::inet"
)
@Column(
        name = "ip_address",
        columnDefinition = "inet",
        nullable = false
)
private String ipAddress;

    // Địa chỉ MAC tương ứng
    @Column(
        name = "mac_address",
        columnDefinition = "macaddr",
        nullable = false
    )
    private String macAddress;

    // Thiết bị mạng ghi nhận địa chỉ IP/MAC
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    // Chỉ số interface, có thể NULL
    @Column(name = "interface_index")
    private Integer interfaceIndex;

    // VLAN ID, có thể NULL
    @Column(name = "vlan_id")
    private Integer vlanId;

    // Thời điểm bản ghi được cập nhật
    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}

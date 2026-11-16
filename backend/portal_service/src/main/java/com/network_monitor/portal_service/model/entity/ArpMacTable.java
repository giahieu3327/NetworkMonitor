package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "arp_mac_tables",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_arp_mac_tables_device_ip",
            columnNames = {"device_id", "ip_address"}
        )
    },
    indexes = {
        @Index(name = "idx_arp_mac_tables_ip", columnList = "ip_address"),
        @Index(name = "idx_arp_mac_tables_mac", columnList = "mac_address")
    }
)
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

    // PostgreSQL INET.
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

    // PostgreSQL MACADDR.
    @Column(
        name = "mac_address",
        columnDefinition = "macaddr",
        nullable = false
    )
    private String macAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    // Có thể NULL nếu không xác định được interface.
    // Không khai báo quan hệ DeviceInterface vì schema không có FK này.
    @Column(name = "interface_index")
    private Integer interfaceIndex;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
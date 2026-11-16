package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(
        name = "credential_name",
        length = 100,
        nullable = false
    )
    private String credentialName;

    // Hiện tại chỉ sử dụng SNMP.
    @Column(
        name = "credential_type",
        length = 20,
        nullable = false
    )
    private String credentialType;

    // SNMPV1, SNMPV2C hoặc SNMPV3.
    @Column(
        name = "protocol_type",
        length = 20,
        nullable = false
    )
    private String protocolType;

    // Community string cho SNMPv1/SNMPv2c.
    // Có thể NULL nếu sử dụng SNMPv3.
    @Column(
        name = "community_string",
        columnDefinition = "text"
    )
    private String communityString;

    @Column(name = "port")
    private Integer port;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
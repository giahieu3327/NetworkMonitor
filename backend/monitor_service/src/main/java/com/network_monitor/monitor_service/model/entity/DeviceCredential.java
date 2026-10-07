package com.network_monitor.monitor_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "device_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceCredential {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false
    )
    private Device device;

    @Column(
            name = "protocol_type",
            nullable = false
    )
    private String protocolType;

    @Builder.Default
    @Column(
            name = "port",
            nullable = false
    )
    private Integer port = 161;

    @Column(name = "community_string")
    private String communityString;

    @Builder.Default
    @Column(
            name = "is_primary",
            nullable = false
    )
    private Integer isPrimary = 0;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private String createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private String updatedAt;
}
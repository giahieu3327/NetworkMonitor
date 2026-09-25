package com.network_monitor.portal_service.model.entity;

import com.network_monitor.portal_service.model.enums.ProtocolType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_credentials")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol_type", length = 20, nullable = false)
    private ProtocolType protocolType;

    @Builder.Default
    @Column(nullable = false)
    private Integer port = 161;

    @Column(name = "community_string", length = 100)
    private String communityString;

    @Builder.Default
    @Column(name = "is_primary")
    private Boolean isPrimary = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
package com.monitor.backend.model.entity;

import com.monitor.backend.model.enums.ProtocolType;
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

    @Column(nullable = false)
    private Integer port;

    @Column(name = "community_string", length = 100)
    private String communityString;

    @Column(name = "snmpv3_username", length = 100)
    private String snmpv3Username;

    @Column(name = "snmpv3_auth_pass", length = 100)
    private String snmpv3AuthPass;

    @Column(name = "api_token", columnDefinition = "TEXT")
    private String apiToken;

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

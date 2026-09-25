package com.network_monitor.portal_service.model.entity;

import com.network_monitor.portal_service.model.enums.AdminStatus;
import com.network_monitor.portal_service.model.enums.OperStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_interfaces")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceInterface {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "interface_index", nullable = false)
    private Integer interfaceIndex;

    @Column(name = "interface_name", length = 50, nullable = false)
    private String interfaceName;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Builder.Default
    @Column(name = "speed_bps")
    private Long speedBps = 1000000000L;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "admin_status", length = 10)
    private AdminStatus adminStatus = AdminStatus.UP;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "oper_status", length = 10)
    private OperStatus operStatus = OperStatus.UP;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
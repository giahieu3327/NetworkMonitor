
package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "device_interfaces")
@IdClass(DeviceInterface.DeviceInterfaceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceInterface {

    // Khóa chính kép: device_id + interface_index.
    @Id
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Id
    @Column(name = "interface_index", nullable = false)
    private Integer interfaceIndex;

    // Quan hệ chỉ đọc vì deviceId quản lý khóa ngoại.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "device_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false
    )
    private Device device;

    // Tên interface.
    // SQL cho phép NULL.
    @Column(name = "interface_name", length = 150)
    private String interfaceName;

    // IANA ifType.
    @Column(name = "interface_type")
    private Integer interfaceType;

    // MTU tính bằng byte.
    @Column(name = "mtu")
    private Integer mtu;

    // Tốc độ tính bằng bit/giây.
    @Column(name = "speed_bps")
    private Long speedBps;

    @Column(name = "phys_address", length = 64)
    private String physAddress;

    // UP, DOWN, TESTING, UNKNOWN.
    @Column(name = "admin_status", length = 30)
    private String adminStatus;

    // UP, DOWN, TESTING, UNKNOWN, DORMANT,
    // NOT_PRESENT, LOWER_LAYER_DOWN.
    @Column(name = "oper_status", length = 30)
    private String operStatus;

    @Column(name = "interface_alias", columnDefinition = "text")
    private String interfaceAlias;

    // IP đại diện của interface.
    // PostgreSQL INET.
    @ColumnTransformer(
        read = "ip_address::text",
        write = "?::inet"
    )
    @Column(
        name = "ip_address",
        columnDefinition = "inet"
    )
    private String ipAddress;

    // Subnet mask của interface.
    // PostgreSQL INET.
    @ColumnTransformer(
        read = "subnet_mask::text",
        write = "?::inet"
    )
    @Column(
        name = "subnet_mask",
        columnDefinition = "inet"
    )
    private String subnetMask;

    // Chỉ interface được bật mới được polling định kỳ.
    @Builder.Default
    @Column(name = "is_monitor", nullable = false)
    private Boolean isMonitor = false;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class DeviceInterfaceId implements Serializable {

        private Long deviceId;

        private Integer interfaceIndex;
    }
}

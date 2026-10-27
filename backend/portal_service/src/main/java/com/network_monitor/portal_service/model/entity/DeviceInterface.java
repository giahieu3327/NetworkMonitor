package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

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

    // Khóa chính thứ nhất: ID thiết bị.
    @Id
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    // Khóa chính thứ hai: chỉ số interface trên thiết bị.
    @Id
    @Column(name = "interface_index", nullable = false)
    private Integer interfaceIndex;

    // Quan hệ với thiết bị.
    // deviceId được quản lý bởi trường khóa phía trên.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Device device;

    // Tên interface.
    @Column(
            name = "interface_name",
            length = 150,
            nullable = false
    )
    private String interfaceName;

    // Tốc độ interface tính bằng bit/giây.
    @Column(name = "speed_bps")
    private Long speedBps;

    // Trạng thái quản trị, ví dụ UP hoặc DOWN.
    @Column(
            name = "admin_status",
            length = 30,
            nullable = false
    )
    private String adminStatus;

    // Trạng thái hoạt động thực tế, ví dụ UP hoặc DOWN.
    @Column(
            name = "oper_status",
            length = 30,
            nullable = false
    )
    private String operStatus;

    // Thời điểm cập nhật gần nhất.
    // Trigger PostgreSQL tự cập nhật khi UPDATE.
    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // Lớp định nghĩa khóa chính kép.
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
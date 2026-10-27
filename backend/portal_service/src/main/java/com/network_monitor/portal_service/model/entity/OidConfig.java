package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "oid_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OidConfig {

    // ID cấu hình OID, tự động tăng bằng BIGSERIAL.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Phạm vi thu thập: DEVICE hoặc INTERFACE.
    @Builder.Default
    @Column(
            name = "metric_scope",
            length = 20,
            nullable = false
    )
    private String metricScope = "DEVICE";

    // Loại chỉ số, ví dụ CPU_USAGE, MEMORY_USAGE, INBOUND_TRAFFIC.
    @Column(
            name = "metric_type",
            length = 100,
            nullable = false
    )
    private String metricType;

    // Mẫu OID dùng để truy vấn SNMP.
    @Column(
            name = "oid_pattern",
            columnDefinition = "text",
            nullable = false
    )
    private String oidPattern;

    // Kiểu dữ liệu nhận được từ SNMP.
    @Builder.Default
    @Column(
            name = "data_type",
            length = 30,
            nullable = false
    )
    private String dataType = "INTEGER";

    // Hệ số chuyển đổi giá trị SNMP.
    @Builder.Default
    @Column(
            name = "multiplier",
            nullable = false
    )
    private Double multiplier = 1.0;

    // Thiết bị áp dụng cấu hình OID.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false
    )
    private Device device;

    // Chỉ số interface; NULL khi metric_scope = DEVICE.
    @Column(name = "interface_index")
    private Integer interfaceIndex;

    // Trạng thái kích hoạt cấu hình.
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Thời điểm tạo bản ghi.
    // PostgreSQL DEFAULT CURRENT_TIMESTAMP tự thiết lập.
    @Builder.Default
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Thời điểm cập nhật gần nhất.
    // Trigger PostgreSQL tự cập nhật khi UPDATE.
    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // Interface được tham chiếu khi cấu hình áp dụng cho interface.
    // Quan hệ này dùng chung device_id và interface_index với các
    // trường phía trên nên không được ghi trực tiếp qua quan hệ.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(
                    name = "device_id",
                    referencedColumnName = "device_id",
                    insertable = false,
                    updatable = false
            ),
            @JoinColumn(
                    name = "interface_index",
                    referencedColumnName = "interface_index",
                    insertable = false,
                    updatable = false
            )
    })
    private DeviceInterface deviceInterface;
}
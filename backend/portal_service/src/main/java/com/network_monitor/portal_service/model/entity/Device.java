
package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_name", length = 150, nullable = false, unique = true)
    private String deviceName;

    // PostgreSQL INET
    @ColumnTransformer(
            read = "host(ip_address)",
            write = "?::inet"
    )
    @Column(name = "ip_address", columnDefinition = "inet",
            nullable = false, unique = true)
    private String ipAddress;

    // ROUTER, SWITCH, FIREWALL...
    @Column(name = "device_type", length = 50, nullable = false)
    private String deviceType;

    // UP, DOWN, UNKNOWN
    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "UNKNOWN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;

        if (status == null) {
            status = "UNKNOWN";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

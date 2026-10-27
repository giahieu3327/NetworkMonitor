package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "syslogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Syslog {

    // ID bản ghi syslog, tự động tăng bằng BIGSERIAL.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Thiết bị phát sinh syslog; có thể NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    // Địa chỉ IP, tương ứng PostgreSQL INET.
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

    // Mức độ nghiêm trọng của syslog.
    // Ví dụ: INFO, WARNING, CRITICAL, UNKNOWN.
    @Column(
            name = "severity",
            length = 20,
            nullable = false
    )
    private String severity;

    // Nội dung thông điệp syslog.
    @Column(
            name = "message",
            columnDefinition = "text",
            nullable = false
    )
    private String message;

    // Dòng syslog nguyên bản; có thể NULL.
    @Column(
            name = "raw_log",
            columnDefinition = "text"
    )
    private String rawLog;

    // Thời điểm hệ thống nhận syslog.
    @Builder.Default
    @Column(
            name = "received_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime receivedAt = OffsetDateTime.now();
}
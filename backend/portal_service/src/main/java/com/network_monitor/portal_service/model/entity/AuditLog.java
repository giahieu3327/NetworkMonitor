
package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Người thực hiện hành động; có thể NULL
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Hành động được ghi nhận
    @Column(name = "action", length = 100, nullable = false)
    private String action;

    // Phân hệ thực hiện hành động
    @Column(name = "module", length = 100, nullable = false)
    private String module;

    // Địa chỉ IP của người thực hiện; có thể NULL
    @Column(name = "entity_id", length = 100)
    private String entityid;

    // Chi tiết log lưu dưới dạng JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details;

    // Thời điểm ghi nhận hành động
    @Builder.Default
    @Column(name = "timestamp", nullable = false, updatable = false)
    private OffsetDateTime timestamp = OffsetDateTime.now();
}

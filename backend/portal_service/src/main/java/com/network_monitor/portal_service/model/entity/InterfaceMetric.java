package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "interface_metrics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterfaceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interface_id", nullable = false)
    private DeviceInterface deviceInterface;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metrics;

    @Builder.Default
    @Column(nullable = false)
    private OffsetDateTime timestamp = OffsetDateTime.now();
}
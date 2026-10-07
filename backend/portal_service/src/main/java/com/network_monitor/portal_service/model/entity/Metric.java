package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            nullable = false
    )
    private Device device;

    @Column(
            name = "interface_index"
    )
    private Integer interfaceIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "metrics",
            columnDefinition = "jsonb",
            nullable = false
    )
    private Map<String, Object> metrics;

    @Builder.Default
    @Column(
            name = "timestamp",
            nullable = false
    )
    private LocalDateTime timestamp = LocalDateTime.now();
}
package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "topology_maps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopologyMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "map_name",
            length = 255,
            nullable = false,
            unique = true
    )
    private String mapName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "nodes_data",
            columnDefinition = "jsonb",
            nullable = false
    )
    private Object nodesData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "edges_data",
            columnDefinition = "jsonb",
            nullable = false
    )
    private Object edgesData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(
            name = "updated_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
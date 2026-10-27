package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

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
    @Column(name = "id")
    private Long id;

    // Tên sơ đồ mạng
    @Column(name = "map_name", length = 255, nullable = false, unique = true)
    private String mapName;

    // Dữ liệu các node lưu dưới dạng JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nodes_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> nodesData;

    // Dữ liệu các edge lưu dưới dạng JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "edges_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> edgesData;

    // Thời điểm tạo sơ đồ
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // Thời điểm cập nhật sơ đồ
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}

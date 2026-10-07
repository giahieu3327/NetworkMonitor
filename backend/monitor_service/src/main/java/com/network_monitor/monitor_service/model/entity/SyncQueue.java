package com.network_monitor.monitor_service.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sync_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(
            name = "entity_type",
            nullable = false
    )
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(
            name = "operation",
            nullable = false
    )
    private String operation;

    @Column(
            name = "payload",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String payload;

    @Builder.Default
    @Column(
            name = "status",
            nullable = false
    )
    private String status = "PENDING";

    @Builder.Default
    @Column(
            name = "retry_count",
            nullable = false
    )
    private Integer retryCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private String createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private String updatedAt;
}
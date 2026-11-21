package com.network_monitor.portal_service.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "approval_tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "parent_device_id")
    private Long parentDeviceId;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private String beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "process_instance_id", unique = true, length = 64)
    private String processInstanceId;

    @Column(name = "maker_id", nullable = false, length = 36)
    private String makerId;

    @Column(name = "checker_id", length = 36)
    private String checkerId;

    @Column(name = "checker_comment")
    private String checkerComment;

    @Column(name = "execution_error")
    private String executionError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;
}
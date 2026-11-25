package com.network_monitor.portal_service.model.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ApprovalTicketResponse {
    private Long id;
    private String entityType;
    private String actionType;
    private Long targetId;
    private Long parentDeviceId;
    private String summary;

    @JsonRawValue
    private String beforeData;

    @JsonRawValue
    private String payload;

    private String status;
    private String processInstanceId;
    private String makerId;
    private String checkerId;
    private String checkerComment;
    private String executionError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime executedAt;

    public static ApprovalTicketResponse fromEntity(ApprovalTicket ticket) {
        return ApprovalTicketResponse.builder()
                .id(ticket.getId())
                .entityType(ticket.getEntityType())
                .actionType(ticket.getActionType())
                .targetId(ticket.getTargetId())
                .parentDeviceId(ticket.getParentDeviceId())
                .summary(ticket.getSummary())
                .beforeData(ticket.getBeforeData())
                .payload(ticket.getPayload())
                .status(ticket.getStatus())
                .processInstanceId(ticket.getProcessInstanceId())
                .makerId(ticket.getMakerId())
                .checkerId(ticket.getCheckerId())
                .checkerComment(ticket.getCheckerComment())
                .executionError(ticket.getExecutionError())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .reviewedAt(ticket.getReviewedAt())
                .executedAt(ticket.getExecutedAt())
                .build();
    }
}
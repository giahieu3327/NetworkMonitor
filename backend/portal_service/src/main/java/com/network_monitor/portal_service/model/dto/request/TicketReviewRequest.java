package com.network_monitor.portal_service.model.dto.request;

import lombok.Data;

@Data
public class TicketReviewRequest {
    private boolean approved;
    private String comment;
}
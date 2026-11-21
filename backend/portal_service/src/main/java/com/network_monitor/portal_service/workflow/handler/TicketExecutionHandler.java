package com.network_monitor.portal_service.workflow.handler;

import com.network_monitor.portal_service.model.entity.ApprovalTicket;

public interface TicketExecutionHandler {
    String getSupportedEntityType();
    void execute(ApprovalTicket ticket) throws Exception;
}
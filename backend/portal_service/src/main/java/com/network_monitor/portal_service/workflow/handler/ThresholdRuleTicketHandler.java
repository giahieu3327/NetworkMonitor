package com.network_monitor.portal_service.workflow.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.portal_service.model.dto.request.ThresholdRuleRequest;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.service.impl.ThresholdRuleServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ThresholdRuleTicketHandler implements TicketExecutionHandler {

    private final ThresholdRuleServiceImpl thresholdRuleService;
    private final ObjectMapper objectMapper;

    @Override
    public String getSupportedEntityType() {
        return "THRESHOLD_RULE";
    }

    @Override
    public void execute(ApprovalTicket ticket) throws Exception {
        String action = ticket.getActionType();

        switch (action) {
            case "CREATE" -> {
                ThresholdRuleRequest req = objectMapper.readValue(
                        ticket.getPayload(),
                        ThresholdRuleRequest.class
                );
                if (ticket.getParentDeviceId() != null && req.getDeviceId() == null) {
                    req.setDeviceId(ticket.getParentDeviceId());
                }
                thresholdRuleService.executeCreateRule(req);
            }
            case "BULK_IMPORT" -> {
                List<ThresholdRuleRequest> list = objectMapper.readValue(
                        ticket.getPayload(),
                        new TypeReference<>() {}
                );
                thresholdRuleService.executeBulkImportRules(ticket.getParentDeviceId(), list);
            }
            case "UPDATE" -> {
                ThresholdRuleRequest req = objectMapper.readValue(
                        ticket.getPayload(),
                        ThresholdRuleRequest.class
                );
                thresholdRuleService.executeUpdateRule(ticket.getTargetId(), req);
            }
            case "DELETE" -> thresholdRuleService.executeDeleteRule(ticket.getTargetId());
            default -> throw new IllegalArgumentException("Không hỗ trợ actionType: " + action);
        }
    }
}
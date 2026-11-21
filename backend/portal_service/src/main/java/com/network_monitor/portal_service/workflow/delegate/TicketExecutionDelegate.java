package com.network_monitor.portal_service.workflow.delegate;

import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.repository.ApprovalTicketRepository;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.workflow.handler.TicketExecutionHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component("ticketExecutionDelegate")
@Slf4j
public class TicketExecutionDelegate implements JavaDelegate {

    @PersistenceContext
    private EntityManager entityManager;

    private final ApprovalTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final HistoryService historyService;
    private final TransactionTemplate requiresNewTx;
    private final Map<String, TicketExecutionHandler> handlerMap;

    public TicketExecutionDelegate(
            ApprovalTicketRepository ticketRepository,
            UserRepository userRepository,
            HistoryService historyService,
            PlatformTransactionManager transactionManager,
            List<TicketExecutionHandler> handlers) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.historyService = historyService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(
                        TicketExecutionHandler::getSupportedEntityType,
                        Function.identity()
                ));
    }

    @Override
    public void execute(DelegateExecution execution) {
        Long ticketId = ((Number) execution.getVariable("ticketId")).longValue();
        ApprovalTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ticket #" + ticketId));

        syncCamundaApprovalMetadata(ticket, execution);

        TicketExecutionHandler handler = handlerMap.get(ticket.getEntityType());
        if (handler == null) {
            markFailed(ticket, execution, "Chưa cấu hình Handler cho entityType: " + ticket.getEntityType());
            return;
        }

        final ApprovalTicket ticketSnapshot = ticket;
        try {
            requiresNewTx.executeWithoutResult(status -> {
                try {
                    handler.execute(ticketSnapshot);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            entityManager.detach(ticket);
            ApprovalTicket refreshed = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ticket #" + ticketId));

            refreshed.setStatus("APPROVED");
            refreshed.setExecutedAt(OffsetDateTime.now());
            ticketRepository.saveAndFlush(refreshed);
            execution.setVariable("executionSuccess", true);
        } catch (Exception e) {
            log.error("Ticket execution failed: ticketId={}", ticketId, e);
            entityManager.detach(ticket);
            ApprovalTicket refreshed = ticketRepository.findById(ticketId).orElse(ticket);
            markFailed(refreshed, execution, getRootMessage(e));
        }
    }

    private void syncCamundaApprovalMetadata(ApprovalTicket ticket, DelegateExecution execution) {
        if (!"PENDING".equals(ticket.getStatus())) {
            return;
        }

        ticket.setStatus("APPROVED");
        ticket.setReviewedAt(OffsetDateTime.now());

        Object commentVar = execution.getVariable("checkerComment");
        if (commentVar != null && ticket.getCheckerComment() == null) {
            ticket.setCheckerComment(commentVar.toString());
        }

        historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(execution.getProcessInstanceId())
                .finished()
                .list()
                .stream()
                .max(Comparator.comparing(HistoricTaskInstance::getEndTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(HistoricTaskInstance::getAssignee)
                .filter(assignee -> assignee != null && !assignee.isBlank() && !assignee.equals(ticket.getMakerId()))
                .filter(userRepository::existsById)
                .ifPresent(ticket::setCheckerId);

        ticketRepository.saveAndFlush(ticket);
    }

    private void markFailed(ApprovalTicket ticket, DelegateExecution execution, String errorMsg) {
        ticket.setStatus("FAILED");
        ticket.setExecutionError(errorMsg);
        ticketRepository.saveAndFlush(ticket);
        execution.setVariable("executionSuccess", false);
    }

    private String getRootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : "Lỗi thực thi không xác định";
    }
}
package com.network_monitor.portal_service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.repository.ApprovalTicketRepository;
import com.network_monitor.portal_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalWorkflowService {

    private static final String PROCESS_KEY = "maker-checker-approval";

    private final ApprovalTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ApprovalTicket submitTicket(
            String entityType,
            String actionType,
            Long targetId,
            Long parentDeviceId,
            String summary,
            Object beforeDataObj,
            Object payloadObj) throws Exception {

        String makerId = getCurrentUserId();

        String beforeJson = beforeDataObj == null ? null : objectMapper.writeValueAsString(beforeDataObj);
        String payloadJson = payloadObj == null ? null : objectMapper.writeValueAsString(payloadObj);

        ApprovalTicket ticket = ApprovalTicket.builder()
                .entityType(entityType)
                .actionType(actionType)
                .targetId(targetId)
                .parentDeviceId(parentDeviceId)
                .summary(summary)
                .beforeData(beforeJson)
                .payload(payloadJson)
                .status("PENDING")
                .makerId(makerId)
                .build();

        ApprovalTicket saved = ticketRepository.saveAndFlush(ticket);

        Map<String, Object> variables = new HashMap<>();
        variables.put("ticketId", saved.getId());
        variables.put("entityType", entityType);
        variables.put("actionType", actionType);
        variables.put("summary", summary);
        variables.put("makerId", makerId);

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                PROCESS_KEY,
                "TICKET_" + saved.getId(),
                variables
        );

        saved.setProcessInstanceId(instance.getId());
        return ticketRepository.saveAndFlush(saved);
    }

    public ApiResponse<Void> reviewTicket(Long ticketId, boolean approved, String comment) {
        try {
            ApprovalTicket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ticket #" + ticketId));

            if (!"PENDING".equals(ticket.getStatus())) {
                return ApiResponse.error("Ticket đã được xử lý trước đó");
            }

            String checkerId = getCurrentUserId();
            if (checkerId.equals(ticket.getMakerId())) {
                return ApiResponse.error("Maker không được tự phê duyệt ticket của chính mình");
            }

            Task task = taskService.createTaskQuery()
                    .processInstanceId(ticket.getProcessInstanceId())
                    .active()
                    .singleResult();

            if (task == null) {
                return ApiResponse.error("Không tìm thấy tác vụ Camunda đang chờ duyệt");
            }

            ticket.setCheckerId(checkerId);
            ticket.setCheckerComment(comment);
            ticket.setStatus(approved ? "APPROVED" : "REJECTED");
            ticket.setReviewedAt(OffsetDateTime.now());
            ticketRepository.saveAndFlush(ticket);

            Map<String, Object> variables = new HashMap<>();
            variables.put("approved", approved);
            variables.put("checkerId", checkerId);
            variables.put("checkerComment", comment == null ? "" : comment);

            taskService.claim(task.getId(), checkerId);
            taskService.complete(task.getId(), variables);

            ApprovalTicket latest = ticketRepository.findById(ticketId).orElse(ticket);
            if ("FAILED".equals(latest.getStatus())) {
                return ApiResponse.error("Đã duyệt nhưng thực thi thất bại: " + latest.getExecutionError());
            }

            return ApiResponse.success(
                    approved ? "Đã phê duyệt ticket #" + ticketId : "Đã từ chối ticket #" + ticketId,
                    null
            );
        } catch (Exception e) {
            log.error("Review ticket failed: id={}", ticketId, e);
            return ApiResponse.error(getRootMessage(e));
        }
    }

    @Transactional
    public ApiResponse<Void> cancelTicket(Long ticketId) {
        try {
            ApprovalTicket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ticket #" + ticketId));

            if (!"PENDING".equals(ticket.getStatus())) {
                return ApiResponse.error("Chỉ có thể hủy ticket đang ở trạng thái PENDING");
            }

            String currentUserId = getCurrentUserId();
            if (!currentUserId.equals(ticket.getMakerId())) {
                return ApiResponse.error("Chỉ người tạo ticket mới có quyền hủy");
            }

            runtimeService.deleteProcessInstance(ticket.getProcessInstanceId(), "Cancelled by Maker");

            ticket.setStatus("CANCELLED");
            ticketRepository.saveAndFlush(ticket);

            return ApiResponse.success("Đã hủy ticket #" + ticketId, null);
        } catch (Exception e) {
            log.error("Cancel ticket failed: id={}", ticketId, e);
            return ApiResponse.error(getRootMessage(e));
        }
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = null;

        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            userId = jwt.getSubject();
        } else if (auth != null) {
            userId = auth.getName();
        }

        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalStateException("Không xác định được tài khoản người dùng hợp lệ trong hệ thống");
        }

        return userId;
    }

    private String getRootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : "Đã xảy ra lỗi không xác định";
    }
}
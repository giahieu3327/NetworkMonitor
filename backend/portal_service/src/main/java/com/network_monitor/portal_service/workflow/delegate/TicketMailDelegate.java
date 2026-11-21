package com.network_monitor.portal_service.workflow.delegate;

import com.network_monitor.portal_service.model.dto.request.SendMailRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.ApprovalTicketRepository;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

@Component("ticketMailDelegate")
@RequiredArgsConstructor
@Slf4j
public class TicketMailDelegate implements JavaDelegate {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ApprovalTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final HistoryService historyService;
    private final MailService mailService;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {
        Long ticketId = ((Number) execution.getVariable("ticketId")).longValue();
        ApprovalTicket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            return;
        }

        Boolean approved = (Boolean) execution.getVariable("approved");
        if (Boolean.FALSE.equals(approved) && "PENDING".equals(ticket.getStatus())) {
            ticket.setStatus("REJECTED");
            ticket.setReviewedAt(OffsetDateTime.now());

            Object commentVar = execution.getVariable("checkerComment");
            if (commentVar != null) {
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

        User maker = userRepository.findById(ticket.getMakerId()).orElse(null);
        if (maker == null || maker.getEmail() == null || maker.getEmail().isBlank()) {
            log.warn("Cannot send notification email: maker not found or missing email for ticket #{}", ticketId);
            return;
        }

        String checkerName = "Camunda Admin";
        if (ticket.getCheckerId() != null) {
            checkerName = userRepository.findById(ticket.getCheckerId())
                    .map(u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername())
                    .orElse(ticket.getCheckerId());
        }

        String statusLabel;
        String badgeColor;
        switch (ticket.getStatus()) {
            case "APPROVED" -> {
                statusLabel = "ĐÃ PHÊ DUYỆT & THỰC THI";
                badgeColor = "#16a34a";
            }
            case "REJECTED" -> {
                statusLabel = "ĐÃ BỊ TỪ CHỐI";
                badgeColor = "#dc2626";
            }
            case "FAILED" -> {
                statusLabel = "ĐÃ DUYỆT NHƯNG THỰC THI LỖI";
                badgeColor = "#d97706";
            }
            default -> {
                statusLabel = ticket.getStatus();
                badgeColor = "#4b5563";
            }
        }

        String recipientName = maker.getFullName() != null && !maker.getFullName().isBlank()
                ? maker.getFullName()
                : maker.getUsername();

        String reviewedTime = ticket.getReviewedAt() != null
                ? ticket.getReviewedAt().format(DATE_FORMATTER)
                : "";

        String commentText = ticket.getCheckerComment() != null && !ticket.getCheckerComment().isBlank()
                ? escapeHtml(ticket.getCheckerComment())
                : "Không có";

        String errorRow = ticket.getExecutionError() != null && !ticket.getExecutionError().isBlank()
                ? "<tr><td style='padding:8px;border:1px solid #e5e7eb;font-weight:bold;color:#dc2626;'>Chi tiết lỗi</td>"
                  + "<td style='padding:8px;border:1px solid #e5e7eb;color:#dc2626;'>" + escapeHtml(ticket.getExecutionError()) + "</td></tr>"
                : "";

        String htmlContent = """
                <div style="font-family:Arial,sans-serif;max-width:620px;margin:0 auto;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                  <div style="background-color:#1e3a8a;color:#ffffff;padding:16px 24px;">
                    <h2 style="margin:0;font-size:18px;">NetworkMonitor - Thông báo phê duyệt Ticket #%d</h2>
                  </div>
                  <div style="padding:24px;color:#1f2937;line-height:1.5;">
                    <p>Xin chào <strong>%s</strong>,</p>
                    <p>Yêu cầu thay đổi cấu hình hệ thống của bạn đã được xử lý với kết quả như sau:</p>
                    <table style="width:100%%;border-collapse:collapse;margin:16px 0;font-size:14px;">
                      <tr>
                        <td style="padding:8px;border:1px solid #e5e7eb;font-weight:bold;width:35%%;background:#f9fafb;">Mã Ticket</td>
                        <td style="padding:8px;border:1px solid #e5e7eb;">#%d</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;border:1px solid #e5e7eb;font-weight:bold;background:#f9fafb;">Đối tượng</td>
                        <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;border:1px solid #e5e7eb;font-weight:bold;background:#f9fafb;">Hành động</td>
                        <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;border:1px solid #e5e7eb;font-weight:bold;background:#f9fafb;">Nội dung</td>
                        <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;border:1px solid #e5e7eb;font-weight:bold;background:#f9fafb;">Trạng thái</td>
                        <td style="padding:8px;border:1px solid #e5e7eb;">
                          <span style="display:inline-block;padding:4px 10px;border-radius:4px;background:%s;color:#ffffff;font-weight:bold;font-size:12px;">%s</span>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:8px;border:1px solid #e5e7eb;font-weight:bold;background:#f9fafb;">Người duyệt (Checker)</td>
                        <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;border:1px solid #e5e7eb;font-weight:bold;background:#f9fafb;">Thời gian duyệt</td>
                        <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;border:1px solid #e5e7eb;font-weight:bold;background:#f9fafb;">Ghi chú</td>
                        <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
                      </tr>
                      %s
                    </table>
                    <p style="font-size:12px;color:#6b7280;margin-top:24px;">Đây là email tự động từ hệ thống NetworkMonitor Portal.</p>
                  </div>
                </div>
                """.formatted(
                ticket.getId(),
                escapeHtml(recipientName),
                ticket.getId(),
                escapeHtml(ticket.getEntityType()),
                escapeHtml(ticket.getActionType()),
                escapeHtml(ticket.getSummary()),
                badgeColor,
                statusLabel,
                escapeHtml(checkerName),
                escapeHtml(reviewedTime),
                commentText,
                errorRow
        );

        SendMailRequest mailRequest = SendMailRequest.builder()
                .toEmail(maker.getEmail())
                .subject("[NetworkMonitor] Ticket #" + ticket.getId() + " - " + statusLabel)
                .content(htmlContent)
                .build();

        ApiResponse<Void> mailResponse = mailService.sendHtmlEmail(mailRequest);
        if (!mailResponse.isSuccess()) {
            log.error("MailService failed for ticket #{}: {}", ticketId, mailResponse.getMessage());
        }
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
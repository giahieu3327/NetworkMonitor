package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.TicketReviewRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.ApprovalTicketResponse;
import com.network_monitor.portal_service.repository.ApprovalTicketRepository;
import com.network_monitor.portal_service.workflow.ApprovalWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class ApprovalTicketController {

    private final ApprovalTicketRepository ticketRepository;
    private final ApprovalWorkflowService approvalWorkflowService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_NETWORK_ADMIN', 'ROLE_NOC_OPERATOR')")
    public ResponseEntity<ApiResponse<Page<ApprovalTicketResponse>>> getTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String makerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String normalizedStatus = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();
        String normalizedEntity = (entityType == null || entityType.isBlank()) ? null : entityType.trim().toUpperCase();
        String normalizedMaker = (makerId == null || makerId.isBlank()) ? null : makerId.trim();
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<ApprovalTicketResponse> result = ticketRepository
                .searchTickets(normalizedStatus, normalizedEntity, normalizedMaker, normalizedKeyword, pageable)
                .map(ApprovalTicketResponse::fromEntity);

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ticket thành công", result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_NETWORK_ADMIN', 'ROLE_NOC_OPERATOR')")
    public ResponseEntity<ApiResponse<ApprovalTicketResponse>> getTicketById(@PathVariable Long id) {
        return ticketRepository.findById(id)
                .map(ticket -> ResponseEntity.ok(
                        ApiResponse.success("Lấy chi tiết ticket thành công", ApprovalTicketResponse.fromEntity(ticket))
                ))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error("Không tìm thấy ticket #" + id)));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_NETWORK_ADMIN', 'ROLE_NOC_OPERATOR')")
    public ResponseEntity<ApiResponse<Void>> reviewTicket(
            @PathVariable Long id,
            @RequestBody TicketReviewRequest request) {
        return ResponseEntity.ok(
                approvalWorkflowService.reviewTicket(id, request.isApproved(), request.getComment())
        );
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_NETWORK_ADMIN', 'ROLE_NOC_OPERATOR')")
    public ResponseEntity<ApiResponse<Void>> cancelTicket(@PathVariable Long id) {
        return ResponseEntity.ok(approvalWorkflowService.cancelTicket(id));
    }
}
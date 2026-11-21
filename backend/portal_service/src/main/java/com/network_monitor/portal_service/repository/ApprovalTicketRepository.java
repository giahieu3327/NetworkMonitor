package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalTicketRepository extends JpaRepository<ApprovalTicket, Long> {

    @Query("""
            SELECT t FROM ApprovalTicket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:entityType IS NULL OR t.entityType = :entityType)
              AND (:makerId IS NULL OR t.makerId = :makerId)
              AND (:keyword IS NULL OR LOWER(t.summary) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
            """)
    Page<ApprovalTicket> searchTickets(
            @Param("status") String status,
            @Param("entityType") String entityType,
            @Param("makerId") String makerId,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
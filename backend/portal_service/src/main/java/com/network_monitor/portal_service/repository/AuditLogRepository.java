package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.user.id = :userId) AND " +
           "(:module IS NULL OR a.module = :module)")
    Page<AuditLog> filterAuditLogs(
            @Param("userId") String userId,
            @Param("module") String module,
            Pageable pageable);
}
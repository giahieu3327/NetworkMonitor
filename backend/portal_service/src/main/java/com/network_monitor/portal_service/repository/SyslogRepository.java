package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Syslog;
import com.network_monitor.portal_service.model.enums.SyslogSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface SyslogRepository extends JpaRepository<Syslog, Long> {

    Page<Syslog> findByDeviceIdOrderByTimestampDesc(Long deviceId, Pageable pageable);

    @Query("SELECT s FROM Syslog s WHERE " +
           "(:deviceId IS NULL OR s.device.id = :deviceId) AND " +
           "(:severity IS NULL OR s.severity = :severity) AND " +
           "(:keyword IS NULL OR LOWER(s.message) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:startTime IS NULL OR s.timestamp >= :startTime) AND " +
           "(:endTime IS NULL OR s.timestamp <= :endTime)")
    Page<Syslog> searchSyslogs(
            @Param("deviceId") Long deviceId,
            @Param("severity") SyslogSeverity severity,
            @Param("keyword") String keyword,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            Pageable pageable);
}
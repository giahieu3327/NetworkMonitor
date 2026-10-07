package com.network_monitor.monitor_service.repository;

import com.network_monitor.monitor_service.model.entity.Syslog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SyslogRepository
        extends JpaRepository<Syslog, Long> {

    Page<Syslog> findByDeviceIdOrderByTimestampDesc(
            Long deviceId,
            Pageable pageable
    );

    Page<Syslog> findBySeverityOrderByTimestampDesc(
            String severity,
            Pageable pageable
    );

    Page<Syslog> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );
}
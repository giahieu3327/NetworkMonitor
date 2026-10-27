package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Syslog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyslogRepository extends JpaRepository<Syslog, Long> {
}
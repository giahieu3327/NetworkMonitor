package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.NotificationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationConfigRepository extends JpaRepository<NotificationConfig, Long> {
    List<NotificationConfig> findByIsActiveTrue();
}

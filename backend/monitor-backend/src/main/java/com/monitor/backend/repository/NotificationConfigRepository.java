package com.monitor.backend.repository;

import com.monitor.backend.model.entity.NotificationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationConfigRepository extends JpaRepository<NotificationConfig, Long> {
    List<NotificationConfig> findByIsActiveTrue();
}

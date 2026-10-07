package com.network_monitor.monitor_service.repository;

import com.network_monitor.monitor_service.model.entity.SyncQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncQueueRepository
        extends JpaRepository<SyncQueue, Long> {

    List<SyncQueue> findByStatusOrderByCreatedAtAsc(
            String status
    );

    List<SyncQueue> findByEntityTypeAndStatusOrderByCreatedAtAsc(
            String entityType,
            String status
    );
}
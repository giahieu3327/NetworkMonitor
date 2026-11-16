package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Metric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface MetricRepository extends JpaRepository<Metric, Long> {

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            DELETE FROM Metric m
            WHERE m.timestamp < :cutoff
            """)
    int deleteExpiredMetrics(
            @Param("cutoff") OffsetDateTime cutoff
    );
}
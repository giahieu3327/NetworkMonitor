package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Metric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricRepository extends JpaRepository<Metric, Long> {

    // ============================================================
    // DEVICE-LEVEL METRICS
    // interface_index IS NULL
    // ============================================================

    @Query("""
        SELECT m
        FROM Metric m
        WHERE m.device.id = :deviceId
          AND m.interfaceIndex IS NULL
          AND m.timestamp BETWEEN :start AND :end
        ORDER BY m.timestamp ASC
        """)
    List<Metric> findDeviceMetricsBetween(
            @Param("deviceId") Long deviceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        SELECT m
        FROM Metric m
        WHERE m.device.id = :deviceId
          AND m.interfaceIndex IS NULL
        ORDER BY m.timestamp DESC
        """)
    List<Metric> findLatestMetricByDeviceId(
            @Param("deviceId") Long deviceId,
            Pageable pageable
    );


    // ============================================================
    // INTERFACE-LEVEL METRICS
    // interface_index IS NOT NULL
    // ============================================================

    @Query("""
        SELECT m
        FROM Metric m
        WHERE m.device.id = :deviceId
          AND m.interfaceIndex = :interfaceIndex
          AND m.timestamp BETWEEN :start AND :end
        ORDER BY m.timestamp ASC
        """)
    List<Metric> findInterfaceMetricsBetween(
            @Param("deviceId") Long deviceId,
            @Param("interfaceIndex") Integer interfaceIndex,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        SELECT m
        FROM Metric m
        WHERE m.device.id = :deviceId
          AND m.interfaceIndex = :interfaceIndex
        ORDER BY m.timestamp DESC
        """)
    List<Metric> findLatestMetricByInterface(
            @Param("deviceId") Long deviceId,
            @Param("interfaceIndex") Integer interfaceIndex,
            Pageable pageable
    );
}
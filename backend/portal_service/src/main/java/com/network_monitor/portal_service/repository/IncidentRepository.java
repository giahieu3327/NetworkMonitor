package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstByDeviceIdAndMetricTypeAndStatusOrderByCreatedAtDesc(
            Long deviceId,
            String metricType,
            String status
    );

    List<Incident> findByMetricTypeOrderByCreatedAtDesc(String metricType);

    List<Incident> findByDeviceIdAndMetricTypeOrderByCreatedAtDesc(
            Long deviceId,
            String metricType
    );

    List<Incident> findByStatusOrderByCreatedAtDesc(String status);

    List<Incident> findByCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Query("""
        SELECT
            i.metricType,
            SUM(CASE WHEN i.severity = 'WARNING' THEN 1 ELSE 0 END),
            SUM(CASE WHEN i.severity = 'CRITICAL' THEN 1 ELSE 0 END),
            MAX(i.peakValue),
            MIN(i.peakValue),
            AVG(i.peakValue)
        FROM Incident i
        WHERE i.device.id = :deviceId
        GROUP BY i.metricType
        ORDER BY i.metricType ASC
        """)
    List<Object[]> summarizeByDeviceGroupByMetric(@Param("deviceId") Long deviceId);

    @Query("""
        SELECT
            d.id,
            d.deviceName,
            i.metricType,
            COUNT(i.id),
            SUM(CASE WHEN i.severity = 'WARNING' THEN 1 ELSE 0 END),
            SUM(CASE WHEN i.severity = 'CRITICAL' THEN 1 ELSE 0 END),
            MAX(i.peakValue)
        FROM Incident i
        JOIN i.device d
        WHERE i.createdAt >= :start AND i.createdAt < :end
        GROUP BY
            d.id,
            d.deviceName,
            i.metricType
        ORDER BY COUNT(i.id) DESC
        """)
    List<Object[]> summarizeIncidentsBetween(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    List<Incident> findAllByStatusAndCreatedAtBefore(String status, OffsetDateTime threshold);
}
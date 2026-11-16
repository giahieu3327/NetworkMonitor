package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Metric;
import com.network_monitor.portal_service.repository.projection.MetricValueProjection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface MetricRepository extends JpaRepository<Metric, Long> {

    /**
     * Lấy giá trị metric mới nhất theo deviceId và metricName.
     */
    @Query(value = """
        SELECT
            m.device_id AS "deviceId",
            m.metric_data ->> :metricName AS "metricValue",
            m.timestamp AS "timestamp"
        FROM metrics m
        WHERE m.device_id = :deviceId
          AND jsonb_typeof(m.metric_data -> :metricName) = 'number'
        ORDER BY m.timestamp DESC
        """,
        nativeQuery = true)
    List<MetricValueProjection> findLatestMetricValue(
        @Param("deviceId") Long deviceId,
        @Param("metricName") String metricName,
        Pageable pageable
    );

    /**
     * Lấy lịch sử metric theo deviceId và metricName.
     */
    @Query(
        value = """
            SELECT
                m.device_id AS "deviceId",
                m.metric_data ->> :metricName AS "metricValue",
                m.timestamp AS "timestamp"
            FROM metrics m
            WHERE m.device_id = :deviceId
              AND jsonb_typeof(m.metric_data -> :metricName) = 'number'
            ORDER BY m.timestamp DESC
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM metrics m
            WHERE m.device_id = :deviceId
              AND jsonb_typeof(m.metric_data -> :metricName) = 'number'
            """,
        nativeQuery = true
    )
    Page<MetricValueProjection> findMetricHistory(
        @Param("deviceId") Long deviceId,
        @Param("metricName") String metricName,
        Pageable pageable
    );

    /**
     * Xóa metric đã quá 24 giờ.
     */
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
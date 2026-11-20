package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Metric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface MetricRepository extends JpaRepository<Metric, Long> {

    List<Metric> findByDeviceIdAndTimestampGreaterThanEqualAndTimestampLessThan(
            Long deviceId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Modifying
    @Query("DELETE FROM Metric m WHERE m.timestamp < :cutoff")
    int deleteExpiredMetrics(@Param("cutoff") OffsetDateTime cutoff);
}
package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.DeviceMetric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceMetricRepository extends JpaRepository<DeviceMetric, Long> {

    List<DeviceMetric> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
            Long deviceId, OffsetDateTime start, OffsetDateTime end);

    @Query("SELECT dm FROM DeviceMetric dm WHERE dm.device.id = :deviceId ORDER BY dm.timestamp DESC")
    List<DeviceMetric> findLatestMetricByDeviceId(@Param("deviceId") Long deviceId, Pageable pageable);
}
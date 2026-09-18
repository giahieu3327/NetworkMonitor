package com.monitor.backend.repository;

import com.monitor.backend.model.entity.DeviceMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceMetricRepository extends JpaRepository<DeviceMetric, Long> {
    List<DeviceMetric> findByDeviceIdOrderByTimestampDesc(Long deviceId);
}

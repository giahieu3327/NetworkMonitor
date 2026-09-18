package com.monitor.backend.repository;

import com.monitor.backend.model.entity.InterfaceMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterfaceMetricRepository extends JpaRepository<InterfaceMetric, Long> {
    List<InterfaceMetric> findByDeviceInterfaceIdOrderByTimestampDesc(Long interfaceId);
}

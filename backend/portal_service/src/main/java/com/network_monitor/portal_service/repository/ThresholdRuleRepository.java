package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.ThresholdRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThresholdRuleRepository extends JpaRepository<ThresholdRule, Long> {

    boolean existsByDeviceIdAndMetricType(
            Long deviceId,
            String metricType
    );

    boolean existsByDeviceIdAndMetricTypeAndIdNot(
            Long deviceId,
            String metricType,
            Long id
    );

    List<ThresholdRule> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);

    List<ThresholdRule> findByDeviceIdAndIsEnabledTrue(Long deviceId);
}
package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.ThresholdRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThresholdRuleRepository extends JpaRepository<ThresholdRule, Long> {

    List<ThresholdRule> findByIsEnabledTrue();

    List<ThresholdRule> findByMetricTypeAndIsEnabledTrue(String metricType);

    @Query("SELECT t FROM ThresholdRule t WHERE t.isEnabled = true AND " +
           "(t.device.id = :deviceId OR t.device.id IS NULL)")
    List<ThresholdRule> findRulesByDevice(@Param("deviceId") Long deviceId);
}
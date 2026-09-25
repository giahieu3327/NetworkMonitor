package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.OidConfig;
import com.network_monitor.portal_service.model.enums.MetricScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OidConfigRepository extends JpaRepository<OidConfig, Long> {

    List<OidConfig> findByIsActiveTrue();

    List<OidConfig> findByMetricScopeAndIsActiveTrue(MetricScope metricScope);

    @Query("SELECT o FROM OidConfig o WHERE o.isActive = true AND " +
           "(o.device.id = :deviceId OR (o.device.id IS NULL AND o.deviceType = :deviceType) OR " +
           "(o.device.id IS NULL AND o.deviceType IS NULL))")
    List<OidConfig> findActiveOidsForDevice(@Param("deviceId") Long deviceId, @Param("deviceType") String deviceType);
}
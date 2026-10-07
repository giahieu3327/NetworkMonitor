package com.network_monitor.monitor_service.repository;

import com.network_monitor.monitor_service.model.entity.OidConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OidConfigRepository
        extends JpaRepository<OidConfig, Long> {

    List<OidConfig> findByIsActiveTrue();

    List<OidConfig> findByMetricScopeAndIsActiveTrue(
            String metricScope
    );

    List<OidConfig> findByDeviceIdAndIsActiveTrue(
            Long deviceId
    );

    List<OidConfig> findByDeviceTypeAndIsActiveTrue(
            String deviceType
    );
}
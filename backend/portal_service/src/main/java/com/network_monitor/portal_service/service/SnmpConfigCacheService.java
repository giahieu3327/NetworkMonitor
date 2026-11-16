
package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.metric.MetricConfig;

import java.util.List;
import java.util.Map;

public interface SnmpConfigCacheService {

    void loadConfigs();

    void reloadConfigs();

    MetricConfig getDeviceConfig(Long deviceId);

    List<MetricConfig> getAllDeviceConfigs();

    Map<String, Object> getDeviceConfigs();
}

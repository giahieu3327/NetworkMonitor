package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.service.SnmpConfigCacheService;
import com.network_monitor.portal_service.service.SnmpService;
import com.network_monitor.portal_service.service.snmp.ArpScope;
import com.network_monitor.portal_service.service.snmp.DeviceScope;
import com.network_monitor.portal_service.service.snmp.InterfaceScope;
import com.network_monitor.portal_service.service.snmp.MetricScope;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SnmpServiceImpl implements SnmpService {

    private final SnmpConfigCacheService configCacheService;

    private final DeviceScope deviceScope;
    private final InterfaceScope interfaceScope;
    private final MetricScope metricScope;
    private final ArpScope arpScope;

    // =============================================
    // CONFIGURATION
    // =============================================

    @Override
    public void loadConfigs() {
        configCacheService.loadConfigs();
    }

    @Override
    public void reloadConfigs() {
        configCacheService.reloadConfigs();
    }

    @Override
    public Map<String, Object> getDeviceConfigs() {
        return configCacheService.getDeviceConfigs();
    }

    // =============================================
    // DEVICE SCOPE
    // =============================================

    @Override
    public Map<String, Object> pollDeviceScope(Long deviceId) {
        return deviceScope.poll(deviceId);
    }

    // =============================================
    // INTERFACE SCOPE
    // =============================================

    @Override
    public Map<String, Object> pollInterfaceScope(Long deviceId) {
        return interfaceScope.poll(deviceId);
    }

    // =============================================
    // METRIC SCOPE
    // =============================================
    
    @Override
    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Ho_Chi_Minh"
    )
    public void pollMetricScope() {
        metricScope.pollAll();
    }

    // =============================================
    // ARP SCOPE
    // =============================================

    @Override
    public Map<String, Object> pollArpScope(Long deviceId) {
        return arpScope.poll(deviceId);
    }
}
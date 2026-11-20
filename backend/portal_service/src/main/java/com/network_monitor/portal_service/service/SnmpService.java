
package com.network_monitor.portal_service.service;

import java.util.Map;

public interface SnmpService {

    void loadConfigs();

    void reloadConfigs();

    Map<String, Object> getDeviceConfigs();

    Map<String, Object> pollDeviceScope(Long deviceId);

    Map<String, Object> pollInterfaceScope(Long deviceId);

    void pollMetricScope();

    Map<String, Object> pollArpScope(Long deviceId);

    record SnmpCredential(
            String protocolType,
            String communityString,
            Integer port
    ) {
    }
}


package com.network_monitor.monitor_service.service;

import com.network_monitor.monitor_service.model.dto.kafka.oidconfig.OidConfigEventDto;

import java.util.List;
import java.util.Optional;

public interface OidConfigService {

    // CREATE
    void createOidConfig(Object data);

    // UPDATE
    void updateOidConfig(Object data);

    // DELETE
    void deleteOidConfig(Object data);

    // QUERY FROM RAM
    Optional<OidConfigEventDto> getOidConfigById(Long id);

    List<OidConfigEventDto> getOidConfigsByDeviceId(Long deviceId);

    List<OidConfigEventDto> getAllOidConfigs();
}

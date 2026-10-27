
package com.network_monitor.monitor_service.service;

import com.network_monitor.monitor_service.model.dto.kafka.credential.DeviceCredentialEventDto;

import java.util.List;
import java.util.Optional;

public interface DeviceCredentialService {

    // CREATE
    void createCredential(Object data);

    // UPDATE
    void updateCredential(Object data);

    // DELETE
    void deleteCredential(Object data);

    // QUERY FROM RAM
    Optional<DeviceCredentialEventDto> getCredentialById(Long id);

    List<DeviceCredentialEventDto> getCredentialsByDeviceId(Long deviceId);

    List<DeviceCredentialEventDto> getAllCredentials();
}

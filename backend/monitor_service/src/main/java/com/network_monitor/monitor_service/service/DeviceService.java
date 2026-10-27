
package com.network_monitor.monitor_service.service;

import com.network_monitor.monitor_service.model.dto.kafka.device.DeviceEventDto;

import java.util.List;
import java.util.Optional;

public interface DeviceService {

    void createDevice(Object data);

    void updateDevice(Object data);

    void deleteDevice(Object data);

    List<DeviceEventDto> getAllDevices();

    Optional<DeviceEventDto> getDeviceById(Long id);

    void updateStatus();
}

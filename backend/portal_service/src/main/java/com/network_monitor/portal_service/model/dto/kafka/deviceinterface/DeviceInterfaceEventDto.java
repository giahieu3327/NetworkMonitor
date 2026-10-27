package com.network_monitor.portal_service.model.dto.kafka.deviceinterface;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInterfaceEventDto {

    private Long deviceId;
    private Integer interfaceIndex;
    private String interfaceName;
    private String macAddress;
    private String ipAddress;
    private String status;
}
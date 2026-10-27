package com.network_monitor.monitor_service.model.dto.kafka.deviceinterface;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInterfaceDeleteEventDto {

    private Long deviceId;
    private Integer interfaceIndex;
}
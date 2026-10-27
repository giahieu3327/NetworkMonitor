package com.network_monitor.monitor_service.model.dto.kafka.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceEventDto {

    private Long deviceId;
    private String deviceName;
    private String ipAddress;
    private String deviceType;
    private String status;
}
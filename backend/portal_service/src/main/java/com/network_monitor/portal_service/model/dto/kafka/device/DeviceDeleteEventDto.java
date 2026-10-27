package com.network_monitor.portal_service.model.dto.kafka.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDeleteEventDto {

    private Long deviceId;
}
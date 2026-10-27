package com.network_monitor.monitor_service.model.dto.kafka.credential;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCredentialDeleteEventDto {

    private Long credentialId;
    private Long deviceId;
}
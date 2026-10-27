package com.network_monitor.monitor_service.model.dto.kafka.credential;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCredentialSyncResponseDto {

    private List<DeviceCredentialEventDto> credentials;
}
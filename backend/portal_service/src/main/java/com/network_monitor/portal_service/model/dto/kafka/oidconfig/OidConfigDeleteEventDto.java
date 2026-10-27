package com.network_monitor.portal_service.model.dto.kafka.oidconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidConfigDeleteEventDto {

    private Long oidConfigId;
    private Long deviceId;
}
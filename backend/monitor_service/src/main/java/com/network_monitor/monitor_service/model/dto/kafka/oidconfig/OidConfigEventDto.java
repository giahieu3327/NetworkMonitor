package com.network_monitor.monitor_service.model.dto.kafka.oidconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidConfigEventDto {

    private Long id;
    private Long deviceId;
    private String oid;
    private String metricName;
    private String dataType;
    private Double multiplier;
    private Boolean isActive;
}
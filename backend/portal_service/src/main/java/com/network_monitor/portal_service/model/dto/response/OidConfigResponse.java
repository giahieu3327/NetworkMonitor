package com.network_monitor.portal_service.model.dto.response;

import com.network_monitor.portal_service.model.enums.MetricScope;
import com.network_monitor.portal_service.model.enums.OidDataType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidConfigResponse {

    private Long id;
    private MetricScope metricScope;
    private String metricType;
    private String oidPattern;
    private OidDataType dataType;
    private Double multiplier;
    private String deviceType;
    private Long deviceId;
    private Long interfaceId;
    private Boolean isActive;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
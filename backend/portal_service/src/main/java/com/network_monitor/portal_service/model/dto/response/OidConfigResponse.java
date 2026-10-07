package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidConfigResponse {

    private Long id;
    private String metricScope;
    private String metricType;
    private String oidPattern;
    private String dataType;
    private Double multiplier;
    private String deviceType;
    private Long deviceId;
    private Long interfaceId;
    private Boolean isActive;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
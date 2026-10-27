
package com.network_monitor.portal_service.model.dto.response;

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

    private String metricScope;

    private String metricType;

    private String oidPattern;

    private String dataType;

    private Double multiplier;

    private Long deviceId;

    private Integer interfaceIndex;

    private Boolean isActive;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}

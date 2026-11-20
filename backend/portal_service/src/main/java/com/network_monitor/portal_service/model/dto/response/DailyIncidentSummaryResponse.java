package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyIncidentSummaryResponse {

    private Long deviceId;

    private String deviceName;

    private String ipAddress;

    private String metricType;

    @Builder.Default
    private Long totalIncidents = 0L;

    @Builder.Default
    private Long warningCount = 0L;

    @Builder.Default
    private Long criticalCount = 0L;

    @Builder.Default
    private Long openCount = 0L;

    @Builder.Default
    private Long resolvedCount = 0L;

    private Double maxPeakValue;

    private Double minPeakValue;

    private Double avgPeakValue;
}
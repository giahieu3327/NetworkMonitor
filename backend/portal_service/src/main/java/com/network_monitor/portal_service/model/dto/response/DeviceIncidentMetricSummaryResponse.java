package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceIncidentMetricSummaryResponse {

    private String metricType;

    private Long totalWarning;

    private Long totalCritical;

    private Double maxPeakValue;

    private Double minPeakValue;

    private Double avgPeakValue;
}
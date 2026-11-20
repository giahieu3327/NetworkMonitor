package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMetricChartPoint {
    private String timeLabel;
    private String metricName;
    private Double avgValue;
    private Double maxValue;
}
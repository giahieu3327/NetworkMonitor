package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllowedMetricResponse {

    private String metricType;

    private String unit;

    private Double minValue;

    private Double maxValue;

    private String conditionDirection;
}
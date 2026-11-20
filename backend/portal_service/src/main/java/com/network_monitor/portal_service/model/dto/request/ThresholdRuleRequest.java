package com.network_monitor.portal_service.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdRuleRequest {

    private Long deviceId;

    private String metricType;

    private Double warningLimit;

    private Double criticalLimit;

    private Integer consecutiveOccurrences;

    private Boolean isEnabled;
}
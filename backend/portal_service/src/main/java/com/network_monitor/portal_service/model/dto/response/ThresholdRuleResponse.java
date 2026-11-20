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
public class ThresholdRuleResponse {

    private Long id;

    private Long deviceId;

    private String deviceName;

    private String metricType;

    private Double warningLimit;

    private Double criticalLimit;

    private Integer consecutiveOccurrences;

    private Boolean isEnabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
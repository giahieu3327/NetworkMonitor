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
public class IncidentResponse {

    private Long id;

    private Long deviceId;

    private String deviceName;

    private Long thresholdRuleId;

    private String ruleName;

    private String metricType;

    private String severity;

    private String title;

    private String description;

    private Double peakValue;

    private Double thresholdLimit;

    private Integer breachCount;

    private Object breachSamples;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime resolvedAt;
}
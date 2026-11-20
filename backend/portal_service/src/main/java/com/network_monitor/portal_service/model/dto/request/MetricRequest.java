package com.network_monitor.portal_service.model.dto.request;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class MetricRequest {

    private Long deviceId;

    private Map<String, Object> metricData;

    private OffsetDateTime timestamp;
}
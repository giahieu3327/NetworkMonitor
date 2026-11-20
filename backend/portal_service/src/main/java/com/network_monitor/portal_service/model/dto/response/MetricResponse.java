package com.network_monitor.portal_service.model.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricResponse {

    private Long id;

    private Long deviceId;

    private String deviceName;

    private Map<String, Object> metricData;

    private OffsetDateTime timestamp;
}
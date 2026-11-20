package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricAggregateResponse {

    private Long id;

    private Long deviceId;

    private String deviceName;

    private String aggregationWindow;

    private OffsetDateTime periodStart;

    private OffsetDateTime periodEnd;

    private Map<String, Object> aggregatedData;
}
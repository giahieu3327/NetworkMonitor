package com.network_monitor.portal_service.model.dto.kafka.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricCollectDto {

    private Long deviceId;
    private Integer interfaceIndex;
    private LocalDateTime collectedAt;
    private Map<String, Object> metricData;
}
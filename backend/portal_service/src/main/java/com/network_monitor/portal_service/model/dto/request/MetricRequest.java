package com.network_monitor.portal_service.model.dto.request;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricRequest {

    private Long deviceId;

    private Map<String, Object> metricData;

    // Thời điểm thu thập dữ liệu.
    // Nếu null thì sử dụng thời điểm hiện tại.
    private OffsetDateTime timestamp;
}
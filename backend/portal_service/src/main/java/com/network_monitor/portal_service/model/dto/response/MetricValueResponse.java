package com.network_monitor.portal_service.model.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricValueResponse {

    private Long deviceId;

    // Ví dụ: CPU Usage, Mem Usage, Core Usage 1
    private String metricName;

    // Giá trị của metric
    private BigDecimal value;

    // Thời điểm thu thập
    private OffsetDateTime timestamp;
}
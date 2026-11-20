
package com.network_monitor.portal_service.model.dto.response;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricHistoryRow {

    private Long id;

    private Long deviceId;

    private String metricValueJson;

    private OffsetDateTime timestamp;
}

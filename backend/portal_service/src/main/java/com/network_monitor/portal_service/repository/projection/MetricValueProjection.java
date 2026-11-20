package com.network_monitor.portal_service.repository.projection;

import java.time.OffsetDateTime;

public interface MetricValueProjection {

    Long getDeviceId();

    String getMetricValue();

    OffsetDateTime getTimestamp();
}

package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.MetricRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.MetricResponse;

import java.util.List;
import java.util.Map;

public interface MetricService {

    ApiResponse<MetricResponse> insertMetric(MetricRequest request);

    ApiResponse<Integer> deleteExpiredMetrics();

    // Latest metrics from Redis
    ApiResponse<Map<String, Object>> getLatestMetrics(Long deviceId);

    // CPU history from PostgreSQL
    ApiResponse<List<MetricResponse>> getCpuHistory(Long deviceId);

    // RAM history from PostgreSQL
    ApiResponse<List<MetricResponse>> getRamHistory(Long deviceId);
}

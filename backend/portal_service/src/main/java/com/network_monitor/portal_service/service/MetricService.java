package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.MetricRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.MetricResponse;
import com.network_monitor.portal_service.model.dto.response.MetricValueResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MetricService {

    ApiResponse<MetricResponse> insertMetric(MetricRequest request);

    /**
     * Lấy giá trị mới nhất của một metric.
     */
    ApiResponse<MetricValueResponse> getLatestMetricValue(
        Long deviceId,
        String metricName
    );

    /**
     * Lấy lịch sử của một metric.
     */
    Page<MetricValueResponse> getMetricHistory(
        Long deviceId,
        String metricName,
        Pageable pageable
    );
}
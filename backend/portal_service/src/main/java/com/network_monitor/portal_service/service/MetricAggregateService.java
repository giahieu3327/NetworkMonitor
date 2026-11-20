package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.MetricAggregateResponse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface MetricAggregateService {

    void aggregateLastHourMetrics();

    ApiResponse<Integer> aggregateHourlyRange(OffsetDateTime periodStart, OffsetDateTime periodEnd);

    ApiResponse<List<MetricAggregateResponse>> get1DayChartMetrics(Long deviceId, LocalDate date);

    ApiResponse<Integer> deleteExpiredAggregates();
}
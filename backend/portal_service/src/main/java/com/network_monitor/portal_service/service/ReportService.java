package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.response.DailyIncidentSummaryResponse;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {

    List<DailyIncidentSummaryResponse> getDailyIncidentSummary(Long deviceId, LocalDate date);

    byte[] exportDailyIncidentReport(Long deviceId, LocalDate date, String format);

    byte[] exportDeviceMetricLineChart(Long deviceId, LocalDate date, String format);
}
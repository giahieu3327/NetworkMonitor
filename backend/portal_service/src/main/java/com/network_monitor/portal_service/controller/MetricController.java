
package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.MetricRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.MetricResponse;
import com.network_monitor.portal_service.service.MetricService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;

    // ============================================
    // INSERT METRIC
    // ============================================

    @PostMapping
    public ApiResponse<MetricResponse> insertMetric(
            @RequestBody MetricRequest request
    ) {
        return metricService.insertMetric(request);
    }

    // ============================================
    // DELETE EXPIRED METRICS
    // ============================================

    @DeleteMapping("/expired")
    public ApiResponse<Integer> deleteExpiredMetrics() {
        return metricService.deleteExpiredMetrics();
    }

    // ============================================
    // GET LATEST METRICS FROM REDIS
    // ============================================

    @GetMapping("/{deviceId}/latest")
    public ApiResponse<Map<String, Object>> getLatestMetrics(
            @PathVariable Long deviceId
    ) {
        return metricService.getLatestMetrics(deviceId);
    }

    // ============================================
    // GET CPU HISTORY FROM POSTGRESQL
    // ============================================

    @GetMapping("/{deviceId}/cpu/history")
    public ApiResponse<List<MetricResponse>> getCpuHistory(
            @PathVariable Long deviceId
    ) {
        return metricService.getCpuHistory(deviceId);
    }

    // ============================================
    // GET RAM HISTORY FROM POSTGRESQL
    // ============================================

    @GetMapping("/{deviceId}/ram/history")
    public ApiResponse<List<MetricResponse>> getRamHistory(
            @PathVariable Long deviceId
    ) {
        return metricService.getRamHistory(deviceId);
    }
}

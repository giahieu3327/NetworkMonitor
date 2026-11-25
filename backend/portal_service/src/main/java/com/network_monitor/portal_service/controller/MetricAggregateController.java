package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.MetricAggregateResponse;
import com.network_monitor.portal_service.service.MetricAggregateService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/metric-aggregates")
@RequiredArgsConstructor
public class MetricAggregateController {

    private final MetricAggregateService metricAggregateService;

    @GetMapping("/1d")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MetricAggregateResponse>>> get1DayChartMetrics(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        ApiResponse<List<MetricAggregateResponse>> response =
                metricAggregateService.get1DayChartMetrics(deviceId, date);
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/aggregate")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_NETWORK_ADMIN', 'ROLE_NOC_OPERATOR')")
    public ResponseEntity<ApiResponse<Integer>> triggerHourlyAggregation(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime periodStart,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime periodEnd
    ) {
        OffsetDateTime end = periodEnd != null
                ? periodEnd
                : OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime start = periodStart != null
                ? periodStart
                : end.minusHours(1);

        ApiResponse<Integer> response = metricAggregateService.aggregateHourlyRange(start, end);
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @DeleteMapping("/expired")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_NETWORK_ADMIN', 'ROLE_NOC_OPERATOR')")
    public ResponseEntity<ApiResponse<Integer>> deleteExpiredAggregates() {
        ApiResponse<Integer> response = metricAggregateService.deleteExpiredAggregates();
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
}
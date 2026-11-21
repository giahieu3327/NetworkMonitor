package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DailyIncidentSummaryResponse;
import com.network_monitor.portal_service.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/incidents/daily")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DailyIncidentSummaryResponse>>> getDailyIncidentSummary(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        try {
            List<DailyIncidentSummaryResponse> summaries =
                    reportService.getDailyIncidentSummary(deviceId, date);
            return ResponseEntity.ok(
                    ApiResponse.success("Daily incident report summary retrieved successfully", summaries)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to generate daily report summary: " + e.getMessage()));
        }
    }


    @GetMapping("/incidents/daily/export")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_NETWORK_ADMIN', 'ROLE_NOC_OPERATOR', 'ROLE_REPORT_MANAGER')")
    public ResponseEntity<?> exportDailyIncidentReport(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        try {
            byte[] fileBytes = reportService.exportDailyIncidentReport(deviceId, date, format);
            LocalDate targetDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

            boolean isXlsx = "xlsx".equalsIgnoreCase(format);
            MediaType mediaType = isXlsx
                    ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    : MediaType.APPLICATION_PDF;
            String extension = isXlsx ? "xlsx" : "pdf";
            String filename = "daily-incident-report-" + targetDate + "." + extension;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(mediaType)
                    .body(fileBytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to export daily report: " + e.getMessage()));
        }
    }

    @GetMapping("/metrics/1d/export")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_NETWORK_ADMIN', 'ROLE_NOC_OPERATOR', 'ROLE_REPORT_MANAGER')")
    public ResponseEntity<?> exportDeviceMetricLineChart(
            @RequestParam Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        try {
            byte[] fileBytes = reportService.exportDeviceMetricLineChart(deviceId, date, format);
            LocalDate targetDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

            boolean isXlsx = "xlsx".equalsIgnoreCase(format);
            MediaType mediaType = isXlsx
                    ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    : MediaType.APPLICATION_PDF;
            String extension = isXlsx ? "xlsx" : "pdf";
            String filename = "device-" + deviceId + "-metrics-" + targetDate + "." + extension;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(mediaType)
                    .body(fileBytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to export device metric chart: " + e.getMessage()));
        }
    }
}
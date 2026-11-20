package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceIncidentMetricSummaryResponse;
import com.network_monitor.portal_service.model.dto.response.IncidentResponse;
import com.network_monitor.portal_service.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping("/{metricType}")
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> getIncidentsByMetricType(
            @PathVariable("metricType") String metricType,
            @RequestParam(required = false) Long deviceId,
            @ModelAttribute SearchRequest request
    ) {
        try {
            Page<IncidentResponse> result =
                    incidentService.getIncidentsByMetricType(metricType, deviceId, request);
            return ResponseEntity.ok(
                    ApiResponse.success("Incidents retrieved successfully", result)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get incidents: " + e.getMessage()));
        }
    }

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<ApiResponse<Page<DeviceIncidentMetricSummaryResponse>>> getIncidentsByDevice(
            @PathVariable Long deviceId,
            @ModelAttribute SearchRequest request
    ) {
        try {
            Page<DeviceIncidentMetricSummaryResponse> result =
                    incidentService.getIncidentsByDevice(deviceId, request);
            return ResponseEntity.ok(
                    ApiResponse.success("Device incident summary retrieved successfully", result)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get device incident summary: " + e.getMessage()));
        }
    }
}
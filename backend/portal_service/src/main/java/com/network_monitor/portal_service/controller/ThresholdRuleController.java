package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.request.ThresholdRuleRequest;
import com.network_monitor.portal_service.model.dto.response.AllowedMetricResponse;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.ThresholdRuleResponse;
import com.network_monitor.portal_service.service.ThresholdRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/threshold-rules")
@RequiredArgsConstructor
public class ThresholdRuleController {

    private final ThresholdRuleService thresholdRuleService;

    @GetMapping("/device/{deviceId}/allowed-metrics")
    public ResponseEntity<ApiResponse<List<AllowedMetricResponse>>> getAllowedMetrics(
            @PathVariable Long deviceId
    ) {
        ApiResponse<List<AllowedMetricResponse>> response =
                thresholdRuleService.getAllowedMetrics(deviceId);
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ThresholdRuleResponse>> getRuleById(
            @PathVariable Long id
    ) {
        ApiResponse<ThresholdRuleResponse> response = thresholdRuleService.getRuleById(id);
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<Page<ThresholdRuleResponse>> getRulesByDevice(
            @PathVariable Long deviceId,
            @ModelAttribute SearchRequest request
    ) {
        return ResponseEntity.ok(thresholdRuleService.getRulesByDevice(deviceId, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ThresholdRuleResponse>> insertRule(
            @RequestBody ThresholdRuleRequest request
    ) {
        ApiResponse<ThresholdRuleResponse> response = thresholdRuleService.insertRule(request);
        HttpStatus status = response.isSuccess() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping(value = "/device/{deviceId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRulesFromExcel(
            @PathVariable Long deviceId,
            @RequestParam("file") MultipartFile file
    ) {
        ApiResponse<Map<String, Object>> response =
                thresholdRuleService.createRulesFromExcel(deviceId, file);
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/device/{deviceId}/export")
    public ResponseEntity<byte[]> exportRules(
            @PathVariable Long deviceId
    ) throws IOException {
        byte[] content = thresholdRuleService.exportRules(deviceId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
        );
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("threshold_rules_device_" + deviceId + ".xlsx", StandardCharsets.UTF_8)
                        .build()
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadRuleImportTemplate() throws IOException {
        byte[] content = thresholdRuleService.downloadRuleImportTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
        );
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("threshold_rules_import_template.xlsx", StandardCharsets.UTF_8)
                        .build()
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateRule(
            @PathVariable Long id,
            @RequestBody ThresholdRuleRequest request
    ) {
        ApiResponse<Void> response = thresholdRuleService.updateRule(id, request);
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(
            @PathVariable Long id
    ) {
        ApiResponse<Void> response = thresholdRuleService.deleteRule(id);
        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
}
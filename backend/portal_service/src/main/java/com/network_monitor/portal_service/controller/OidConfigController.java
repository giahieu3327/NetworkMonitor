
package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.OidConfigRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.OidConfigResponse;
import com.network_monitor.portal_service.service.OidConfigService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/oid-configs")
@RequiredArgsConstructor
public class OidConfigController {

    private final OidConfigService oidConfigService;

    @PostMapping
    public ResponseEntity<ApiResponse<OidConfigResponse>> createOidConfig(
            @Valid @RequestBody OidConfigRequest request
    ) {
        ApiResponse<OidConfigResponse> response =
                oidConfigService.createOidConfig(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOidConfigsFromExcel(
            @RequestParam("file") MultipartFile file
    ) {
        ApiResponse<Map<String, Object>> response =
                oidConfigService.createOidConfigsFromExcel(file);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

        @GetMapping("/export")
        public ResponseEntity<byte[]> exportOidConfigsToExcel()
                throws IOException {

        byte[] file = oidConfigService.exportOidConfigsToExcel();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"oid-configs.xlsx\""
                )
                .body(file);
        }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateOidConfig(
            @PathVariable Long id,
            @Valid @RequestBody OidConfigRequest request
    ) {
        ApiResponse<Void> response =
                oidConfigService.updateOidConfig(id, request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOidConfig(
            @PathVariable Long id
    ) {
        ApiResponse<Void> response =
                oidConfigService.deleteOidConfig(id);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/device/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteOidConfigsByDeviceId(
            @PathVariable Long deviceId
    ) {
        ApiResponse<Void> response =
                oidConfigService.deleteOidConfigsByDeviceId(deviceId);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OidConfigResponse>> getOidConfigById(
            @PathVariable Long id
    ) {
        ApiResponse<OidConfigResponse> response =
                oidConfigService.getOidConfigById(id);

        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<Page<OidConfigResponse>> getOidConfigsByDeviceId(
            @PathVariable Long deviceId,
            @ModelAttribute SearchRequest request
    ) {
        return ResponseEntity.ok(
                oidConfigService.getOidConfigsByDeviceId(
                        deviceId,
                        request
                )
        );
    }
}

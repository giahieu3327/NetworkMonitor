
package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.OidConfigRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.OidConfigResponse;
import com.network_monitor.portal_service.service.OidConfigService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/oid-configs")
@RequiredArgsConstructor
public class OidConfigController {

    private final OidConfigService oidConfigService;

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // =========================================================
    // CREATE
    // =========================================================

    @PostMapping
    public ResponseEntity<ApiResponse<OidConfigResponse>> createOidConfig(
            @Valid @RequestBody OidConfigRequest request) {

        ApiResponse<OidConfigResponse> response =
                oidConfigService.createOidConfig(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================
    // IMPORT / EXPORT
    // =========================================================

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> importOidConfigs(
            @RequestParam("deviceId") Long deviceId,
            @RequestParam("file") MultipartFile file) {

        ApiResponse<Map<String, Object>> response =
                oidConfigService.createOidConfigsFromExcel(deviceId, file);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportOidConfigs(
            @RequestParam("deviceId") Long deviceId) throws IOException {

        return downloadFile(
                oidConfigService.exportOidConfigs(deviceId),
                "oid-configs-" + deviceId + ".xlsx",
                EXCEL_CONTENT_TYPE
        );
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadOidConfigImportTemplate()
            throws IOException {

        return downloadFile(
                oidConfigService.downloadOidConfigImportTemplate(),
                "oid-configs-import-template.xlsx",
                EXCEL_CONTENT_TYPE
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateOidConfig(
            @PathVariable Long id,
            @Valid @RequestBody OidConfigRequest request) {

        ApiResponse<Void> response =
                oidConfigService.updateOidConfig(id, request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // DELETE
    // =========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOidConfig(
            @PathVariable Long id) {

        ApiResponse<Void> response =
                oidConfigService.deleteOidConfig(id);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/device/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteOidConfigsByDeviceId(
            @PathVariable Long deviceId) {

        ApiResponse<Void> response =
                oidConfigService.deleteOidConfigsByDeviceId(deviceId);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // QUERY
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OidConfigResponse>> getOidConfigById(
            @PathVariable Long id) {

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
            @ModelAttribute SearchRequest request) {

        return ResponseEntity.ok(
                oidConfigService.getOidConfigsByDeviceId(
                        deviceId,
                        request
                )
        );
    }

    // =========================================================
    // COMMON DOWNLOAD HELPER
    // =========================================================

    private ResponseEntity<byte[]> downloadFile(
            byte[] file,
            String filename,
            String contentType) {

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .body(file);
    }
}

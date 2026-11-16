package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.DeviceCredentialRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceCredentialResponse;
import com.network_monitor.portal_service.service.DeviceCredentialService;

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
@RequestMapping("/api/v1/device-credentials")
@RequiredArgsConstructor
public class DeviceCredentialController {

    private final DeviceCredentialService deviceCredentialService;

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // =========================================================
    // CREATE
    // =========================================================

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceCredentialResponse>> addCredential(
            @Valid @RequestBody DeviceCredentialRequest request) {

        ApiResponse<DeviceCredentialResponse> response =
                deviceCredentialService.addCredential(request);

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
    public ResponseEntity<ApiResponse<Map<String, Object>>> importCredentials(
            @RequestParam("file") MultipartFile file) {

        ApiResponse<Map<String, Object>> response =
                deviceCredentialService.createCredentialsFromExcel(file);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCredentials()
            throws IOException {

        return downloadFile(
                deviceCredentialService.exportCredentials(),
                "device-credentials.xlsx",
                EXCEL_CONTENT_TYPE
        );
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadCredentialImportTemplate()
            throws IOException {

        return downloadFile(
                deviceCredentialService.downloadCredentialImportTemplate(),
                "device-credentials-import-template.xlsx",
                EXCEL_CONTENT_TYPE
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateCredential(
            @PathVariable Long id,
            @Valid @RequestBody DeviceCredentialRequest request) {

        ApiResponse<Void> response =
                deviceCredentialService.updateCredential(id, request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // DELETE
    // =========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCredential(
            @PathVariable Long id) {

        ApiResponse<Void> response =
                deviceCredentialService.deleteCredential(id);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/device/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteCredentialsByDeviceId(
            @PathVariable Long deviceId) {

        ApiResponse<Void> response =
                deviceCredentialService.deleteCredentialsByDeviceId(deviceId);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // QUERY
    // =========================================================

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<Page<DeviceCredentialResponse>> getCredentialsByDeviceId(
            @PathVariable Long deviceId,
            @ModelAttribute SearchRequest request) {

        return ResponseEntity.ok(
                deviceCredentialService.getCredentialsByDeviceId(
                        deviceId,
                        request
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceCredentialResponse>> getCredentialById(
            @PathVariable Long id) {

        ApiResponse<DeviceCredentialResponse> response =
                deviceCredentialService.getCredentialById(id);

        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);
        }

        return ResponseEntity.ok(response);
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
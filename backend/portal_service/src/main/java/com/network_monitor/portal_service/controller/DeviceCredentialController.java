
package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.DeviceCredentialRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceCredentialResponse;
import com.network_monitor.portal_service.service.DeviceCredentialService;

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
@RequestMapping("/api/v1/device-credentials")
@RequiredArgsConstructor
public class DeviceCredentialController {

    private final DeviceCredentialService deviceCredentialService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceCredentialResponse>> addCredential(
            @Valid @RequestBody DeviceCredentialRequest request
    ) {
        ApiResponse<DeviceCredentialResponse> response =
                deviceCredentialService.addCredential(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCredentialsFromExcel(
            @RequestParam("file") MultipartFile file
    ) {
        ApiResponse<Map<String, Object>> response =
                deviceCredentialService.createCredentialsFromExcel(file);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

        @GetMapping("/export")
        public ResponseEntity<byte[]> exportCredentialsToExcel()
                throws IOException {

        byte[] file = deviceCredentialService.exportCredentialsToExcel();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"device-credentials.xlsx\""
                )
                .body(file);
        }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateCredential(
            @PathVariable Long id,
            @Valid @RequestBody DeviceCredentialRequest request
    ) {
        ApiResponse<Void> response =
                deviceCredentialService.updateCredential(id, request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCredential(
            @PathVariable Long id
    ) {
        ApiResponse<Void> response =
                deviceCredentialService.deleteCredential(id);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/device/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteCredentialsByDeviceId(
            @PathVariable Long deviceId
    ) {
        ApiResponse<Void> response =
                deviceCredentialService.deleteCredentialsByDeviceId(deviceId);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<Page<DeviceCredentialResponse>> getCredentialsByDeviceId(
            @PathVariable Long deviceId,
            @ModelAttribute SearchRequest request
    ) {
        return ResponseEntity.ok(
                deviceCredentialService.getCredentialsByDeviceId(
                        deviceId,
                        request
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceCredentialResponse>> getCredentialById(
            @PathVariable Long id
    ) {
        ApiResponse<DeviceCredentialResponse> response =
                deviceCredentialService.getCredentialById(id);

        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }
}

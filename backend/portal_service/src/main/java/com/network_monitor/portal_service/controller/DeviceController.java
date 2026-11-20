package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.DeviceRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceResponse;
import com.network_monitor.portal_service.service.DeviceService;

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
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    
    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // =========================================================
    // CREATE
    // =========================================================

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> createDevice(
            @Valid @RequestBody DeviceRequest request) {

        ApiResponse<DeviceResponse> response =
                deviceService.createDevice(request);

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
    public ResponseEntity<ApiResponse<Map<String, Object>>> importDevices(
            @RequestParam("file") MultipartFile file) {

        ApiResponse<Map<String, Object>> response =
                deviceService.createDevicesFromExcel(file);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDevices()
            throws IOException {

        return downloadFile(
                deviceService.exportDevices(),
                "devices.xlsx",
                EXCEL_CONTENT_TYPE
        );
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadDeviceImportTemplate()
            throws IOException {

        return downloadFile(
                deviceService.downloadDeviceImportTemplate(),
                "devices-import-template.xlsx",
                EXCEL_CONTENT_TYPE
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody DeviceRequest request) {

        ApiResponse<Void> response =
                deviceService.updateDevice(id, request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // DELETE
    // =========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(
            @PathVariable Long id) {

        ApiResponse<Void> response = deviceService.deleteDevice(id);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAllDevices() {
        ApiResponse<Void> response = deviceService.deleteAllDevices();

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // QUERY
    // =========================================================

    @GetMapping
    public ResponseEntity<Page<DeviceResponse>> getDevices(
            @ModelAttribute SearchRequest request) {

        return ResponseEntity.ok(deviceService.getDevices(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getDeviceById(
            @PathVariable Long id) {

        ApiResponse<DeviceResponse> response =
                deviceService.getDeviceById(id);

        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeviceInfo(
            @PathVariable Long id) {

        ApiResponse<Map<String, Object>> response =
                deviceService.getDeviceInfo(id);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }


    // Không tạo API updateStatus vì trạng thái được cập nhật background.

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
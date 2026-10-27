
package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.DeviceRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceResponse;
import com.network_monitor.portal_service.service.DeviceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import java.io.IOException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> createDevice(
            @Valid @RequestBody DeviceRequest request
    ) {
        ApiResponse<DeviceResponse> response =
                deviceService.createDevice(request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDevicesFromExcel(
            @RequestParam("file") MultipartFile file
    ) {
        ApiResponse<Map<String, Object>> response =
                deviceService.createDevicesFromExcel(file);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDevicesToExcel() throws IOException {
        byte[] file = deviceService.exportDevicesToExcel();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"devices.xlsx\""
                )
                .body(file);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody DeviceRequest request
    ) {
        ApiResponse<Void> response =
                deviceService.updateDevice(id, request);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(
            @PathVariable Long id
    ) {
        ApiResponse<Void> response =
                deviceService.deleteDevice(id);

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAllDevices() {
        ApiResponse<Void> response =
                deviceService.deleteAllDevices();

        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<DeviceResponse>> getDevices(
            @ModelAttribute SearchRequest request
    ) {
        return ResponseEntity.ok(deviceService.getDevices(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getDeviceById(
            @PathVariable Long id
    ) {
        ApiResponse<DeviceResponse> response =
                deviceService.getDeviceById(id);

        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }
}

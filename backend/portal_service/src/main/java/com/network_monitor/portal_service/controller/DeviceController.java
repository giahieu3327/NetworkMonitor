package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.DeviceRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceResponse;
import com.network_monitor.portal_service.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    // 1. Thêm 1 hoặc nhiều thiết bị (Gửi List JSON)
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createDevices(@Valid @RequestBody List<DeviceRequest> requests) {
        ApiResponse<Void> response = deviceService.createDevices(requests);
        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 2. Cập nhật 1 thiết bị theo ID
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody DeviceRequest request) {
        ApiResponse<Void> response = deviceService.updateDevice(id, request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 3. Xóa nhiều thiết bị cùng lúc (Truyền List ID qua Request Body)
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteDevices(@RequestBody List<Long> ids) {
        ApiResponse<Void> response = deviceService.deleteDevices(ids);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 4. Lấy tất cả thiết bị (GET /api/v1/devices)
    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getAllDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }

    // 5. Lấy danh sách thiết bị theo 1 hoặc nhiều IDs (GET /api/v1/devices/1 HOẶC GET /api/v1/devices/1,3,5)
    // Spring Boot sẽ tự động ép chuỗi phẩy "1,3,5" thành List<Long> [1, 3, 5]
    @GetMapping("/{ids}")
    public ResponseEntity<List<DeviceResponse>> getDevicesByIds(@PathVariable List<Long> ids) {
        return ResponseEntity.ok(deviceService.getDevicesByIds(ids));
    }
}
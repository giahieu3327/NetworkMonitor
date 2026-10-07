package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;
import com.network_monitor.portal_service.service.DeviceCredentialService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/device-credentials")
@RequiredArgsConstructor
public class DeviceCredentialController {

    private final DeviceCredentialService credentialService;

    // 1. Thêm 1 hoặc nhiều cấu hình xác thực (Gửi List JSON)
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> addCredentials(@Valid @RequestBody List<DeviceCredentialRequest> requests) {
        ApiResponse<Void> response = credentialService.addCredentials(requests);
        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 2. Cập nhật 1 cấu hình xác thực theo ID
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateCredential(
            @PathVariable Long id,
            @Valid @RequestBody DeviceCredentialRequest request) {
        ApiResponse<Void> response = credentialService.updateCredential(id, request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 3. Xóa nhiều cấu hình xác thực cùng lúc (Truyền List ID qua Request Body)
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteCredentials(@RequestBody List<Long> ids) {
        ApiResponse<Void> response = credentialService.deleteCredentials(ids);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 4. Lấy toàn bộ cấu hình xác thực (GET /api/v1/device-credentials)
    @GetMapping
    public ResponseEntity<List<DeviceCredentialResponse>> getAllCredentials() {
        return ResponseEntity.ok(credentialService.getAllCredentials());
    }

    // 5. Lấy danh sách cấu hình xác thực theo 1 hoặc nhiều IDs (GET /api/v1/device-credentials/1 hoặc /1,3,5)
    @GetMapping("/{ids}")
    public ResponseEntity<List<DeviceCredentialResponse>> getCredentialsByIds(@PathVariable List<Long> ids) {
        return ResponseEntity.ok(credentialService.getCredentialsByIds(ids));
    }

    // 6. Lấy danh sách cấu hình xác thực của 1 thiết bị cụ thể (GET /api/v1/device-credentials/device/{deviceId})
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<List<DeviceCredentialResponse>> getCredentialsByDeviceId(@PathVariable Long deviceId) {
        return ResponseEntity.ok(credentialService.getCredentialsByDeviceId(deviceId));
    }
}
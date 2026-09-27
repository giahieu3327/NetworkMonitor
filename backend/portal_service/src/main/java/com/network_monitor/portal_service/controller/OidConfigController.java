package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.OidConfigRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.OidConfigResponse;
import com.network_monitor.portal_service.service.OidConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/oid-configs")
@RequiredArgsConstructor
public class OidConfigController {

    private final OidConfigService oidConfigService;

    // 1. Thêm 1 hoặc nhiều cấu hình OID (Gửi List JSON)
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createOidConfigs(@Valid @RequestBody List<OidConfigRequest> requests) {
        ApiResponse<Void> response = oidConfigService.createOidConfigs(requests);
        if (response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 2. Cập nhật 1 cấu hình OID theo ID
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateOidConfig(
            @PathVariable Long id,
            @Valid @RequestBody OidConfigRequest request) {
        ApiResponse<Void> response = oidConfigService.updateOidConfig(id, request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 3. Xóa nhiều cấu hình OID cùng lúc (Truyền List ID qua Request Body)
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteOidConfigs(@RequestBody List<Long> ids) {
        ApiResponse<Void> response = oidConfigService.deleteOidConfigs(ids);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 4. Lấy toàn bộ cấu hình OID (GET /api/v1/oid-configs)
    @GetMapping
    public ResponseEntity<List<OidConfigResponse>> getAllOidConfigs() {
        return ResponseEntity.ok(oidConfigService.getAllOidConfigs());
    }

    // 5. Lấy danh sách cấu hình OID theo 1 hoặc nhiều IDs (GET /api/v1/oid-configs/1 hoặc /1,3,5)
    @GetMapping("/{ids}")
    public ResponseEntity<List<OidConfigResponse>> getOidConfigsByIds(@PathVariable List<Long> ids) {
        return ResponseEntity.ok(oidConfigService.getOidConfigsByIds(ids));
    }

    // 6. Lấy danh sách cấu hình OID áp dụng cho 1 thiết bị cụ thể (GET /api/v1/oid-configs/device/{deviceId})
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<List<OidConfigResponse>> getOidConfigsByDeviceId(@PathVariable Long deviceId) {
        return ResponseEntity.ok(oidConfigService.getOidConfigsByDeviceId(deviceId));
    }
}
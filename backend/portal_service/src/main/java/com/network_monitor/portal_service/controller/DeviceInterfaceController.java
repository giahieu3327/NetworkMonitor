
package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceInterfaceResponse;

import com.network_monitor.portal_service.service.DeviceInterfaceService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/interfaces")
@RequiredArgsConstructor
public class DeviceInterfaceController {

    private final DeviceInterfaceService interfaceService;


    // ======================================================
    // GET ALL INTERFACES BY DEVICE ID
    // ======================================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<DeviceInterfaceResponse>>>
    getInterfacesByDeviceId(
            @PathVariable Long deviceId,
            @ModelAttribute SearchRequest request) {

        try {

            Page<DeviceInterfaceResponse> result =
                    interfaceService.getInterfacesByDeviceId(
                            deviceId,
                            request
                    );

            return ResponseEntity.ok(
                    ApiResponse.success(
                            "Lấy danh sách interface thành công",
                            result
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(
                            "Không thể lấy danh sách interface"
                    ));
        }
    }

    // ======================================================
    // GET INTERFACE DETAIL
    // ======================================================

    @GetMapping("/{interfaceIndex}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DeviceInterfaceResponse>>
    getInterfaceById(
            @PathVariable Long deviceId,
            @PathVariable Integer interfaceIndex) {

        ApiResponse<DeviceInterfaceResponse> response =
                interfaceService.getInterfaceById(
                        deviceId,
                        interfaceIndex
                );

        if (response.getData() == null) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }
}

package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.ArpMacTableResponse;

import com.network_monitor.portal_service.service.ArpMacTableService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/arp-mac-tables")
@RequiredArgsConstructor
public class ArpMacTableController {

    private final ArpMacTableService arpMacTableService;

    // ======================================================
    // GET ALL ARP MAC TABLES BY DEVICE ID
    // ======================================================

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ArpMacTableResponse>>>
    getArpMacTablesByDeviceId(
            @PathVariable Long deviceId,
            @ModelAttribute SearchRequest request) {

        try {

            Page<ArpMacTableResponse> result =
                    arpMacTableService.getByDeviceId(
                            deviceId,
                            request
                    );

            return ResponseEntity.ok(
                    ApiResponse.success(
                            "Lấy danh sách ARP MAC Table thành công",
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
                            "Không thể lấy danh sách ARP MAC Table"
                    ));
        }
    }

}
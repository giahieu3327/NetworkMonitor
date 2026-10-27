package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.DeviceRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceResponse;

import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.Map;

public interface DeviceService {

    ApiResponse<DeviceResponse> createDevice(DeviceRequest request);

    ApiResponse<Map<String, Object>> createDevicesFromExcel(
            MultipartFile file
    );

    ApiResponse<Void> updateDevice(Long id, DeviceRequest request);

    ApiResponse<Void> deleteDevice(Long id);

    ApiResponse<Void> deleteAllDevices();

    Page<DeviceResponse> getDevices(SearchRequest request);

    ApiResponse<DeviceResponse> getDeviceById(Long id);

    byte[] exportDevicesToExcel() throws IOException;

    ApiResponse<Void> updateStatus(Long id, String status);
}
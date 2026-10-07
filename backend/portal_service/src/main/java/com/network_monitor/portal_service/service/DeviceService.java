package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;

import java.util.List;

public interface DeviceService {
    ApiResponse<Void> createDevices(List<DeviceRequest> requests);
    ApiResponse<Void> updateDevice(Long id, DeviceRequest request);
    ApiResponse<Void> deleteDevices(List<Long> ids);

    // Tách bạch 2 hàm xử lý riêng biệt
    List<DeviceResponse> getAllDevices();
    List<DeviceResponse> getDevicesByIds(List<Long> ids);
}
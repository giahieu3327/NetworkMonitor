
package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.DeviceInterfaceRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceInterfaceResponse;

import org.springframework.data.domain.Page;

public interface DeviceInterfaceService {

    // CREATE
    ApiResponse<DeviceInterfaceResponse> addInterface(
            DeviceInterfaceRequest request
    );

    // UPDATE
    ApiResponse<Void> updateInterface(
            Long deviceId,
            Integer interfaceIndex,
            DeviceInterfaceRequest request
    );

    // DELETE
    ApiResponse<Void> deleteInterface(
            Long deviceId,
            Integer interfaceIndex
    );

    // READ
    Page<DeviceInterfaceResponse> getInterfacesByDeviceId(
            Long deviceId,
            SearchRequest request
    );

    ApiResponse<DeviceInterfaceResponse> getInterfaceById(
            Long deviceId,
            Integer interfaceIndex
    );
}

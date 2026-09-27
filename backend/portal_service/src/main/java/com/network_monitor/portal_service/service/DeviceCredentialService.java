package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.DeviceCredentialRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceCredentialResponse;

import java.util.List;

public interface DeviceCredentialService {
    ApiResponse<Void> addCredentials(List<DeviceCredentialRequest> requests);
    ApiResponse<Void> updateCredential(Long id, DeviceCredentialRequest request);
    ApiResponse<Void> deleteCredentials(List<Long> ids);

    List<DeviceCredentialResponse> getAllCredentials();
    List<DeviceCredentialResponse> getCredentialsByIds(List<Long> ids);
    List<DeviceCredentialResponse> getCredentialsByDeviceId(Long deviceId);
}
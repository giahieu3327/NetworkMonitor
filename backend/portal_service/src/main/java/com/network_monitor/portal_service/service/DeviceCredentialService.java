
package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.DeviceCredentialRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceCredentialResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.Map;

public interface DeviceCredentialService {

    ApiResponse<DeviceCredentialResponse> addCredential(
            DeviceCredentialRequest request
    );

    ApiResponse<Map<String, Object>> createCredentialsFromExcel(
            MultipartFile file
    );

    ApiResponse<Void> updateCredential(
            Long id,
            DeviceCredentialRequest request
    );

    ApiResponse<Void> deleteCredential(Long id);

    ApiResponse<Void> deleteCredentialsByDeviceId(Long deviceId);

    Page<DeviceCredentialResponse> getCredentialsByDeviceId(
            Long deviceId,
            SearchRequest request
    );

    ApiResponse<DeviceCredentialResponse> getCredentialById(Long id);

    byte[] exportCredentialsToExcel() throws IOException;
}

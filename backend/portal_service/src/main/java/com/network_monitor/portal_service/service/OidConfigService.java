
package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.OidConfigRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.OidConfigResponse;

import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.Map;

public interface OidConfigService {

    ApiResponse<OidConfigResponse> createOidConfig(
            OidConfigRequest request
    );

    ApiResponse<Map<String, Object>> createOidConfigsFromExcel(
            MultipartFile file
    );

    ApiResponse<Void> updateOidConfig(
            Long id,
            OidConfigRequest request
    );

    ApiResponse<Void> deleteOidConfig(Long id);

    ApiResponse<Void> deleteOidConfigsByDeviceId(Long deviceId);

    ApiResponse<OidConfigResponse> getOidConfigById(Long id);

    Page<OidConfigResponse> getOidConfigsByDeviceId(
            Long deviceId,
            SearchRequest request
    );

    byte[] exportOidConfigsToExcel() throws IOException;
}

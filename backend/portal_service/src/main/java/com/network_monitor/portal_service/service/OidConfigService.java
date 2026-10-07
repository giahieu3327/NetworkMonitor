package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;

import java.util.List;

public interface OidConfigService {
    ApiResponse<Void> createOidConfigs(List<OidConfigRequest> requests);
    ApiResponse<Void> updateOidConfig(Long id, OidConfigRequest request);
    ApiResponse<Void> deleteOidConfigs(List<Long> ids);

    List<OidConfigResponse> getAllOidConfigs();
    List<OidConfigResponse> getOidConfigsByIds(List<Long> ids);
    List<OidConfigResponse> getOidConfigsByDeviceId(Long deviceId);
}
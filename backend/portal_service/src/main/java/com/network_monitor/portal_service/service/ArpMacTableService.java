package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.ArpMacTableRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.ArpMacTableResponse;

import org.springframework.data.domain.Page;

public interface ArpMacTableService {

    ApiResponse<ArpMacTableResponse> insert(
            ArpMacTableRequest request
    );

    ApiResponse<ArpMacTableResponse> update(
            Long id,
            ArpMacTableRequest request
    );

    ApiResponse<Void> delete(Long id);

    Page<ArpMacTableResponse> getByDeviceId(
            Long deviceId,
            SearchRequest request
    );
}
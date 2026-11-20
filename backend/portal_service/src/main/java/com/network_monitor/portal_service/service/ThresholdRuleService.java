package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.request.ThresholdRuleRequest;
import com.network_monitor.portal_service.model.dto.response.AllowedMetricResponse;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.ThresholdRuleResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ThresholdRuleService {

    ApiResponse<List<AllowedMetricResponse>> getAllowedMetrics(Long deviceId);

    ApiResponse<ThresholdRuleResponse> getRuleById(Long id);

    Page<ThresholdRuleResponse> getRulesByDevice(Long deviceId, SearchRequest request);

    List<ThresholdRuleResponse> getEnabledRulesByDevice(Long deviceId);

    ApiResponse<ThresholdRuleResponse> insertRule(ThresholdRuleRequest request);

    ApiResponse<Map<String, Object>> createRulesFromExcel(Long deviceId, MultipartFile file);

    ApiResponse<Void> updateRule(Long id, ThresholdRuleRequest request);

    ApiResponse<Void> deleteRule(Long id);

    byte[] exportRules(Long deviceId) throws IOException;

    byte[] downloadRuleImportTemplate() throws IOException;
}
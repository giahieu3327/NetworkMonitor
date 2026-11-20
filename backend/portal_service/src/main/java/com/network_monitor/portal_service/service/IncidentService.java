package com.network_monitor.portal_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.DeviceIncidentMetricSummaryResponse;
import com.network_monitor.portal_service.model.dto.response.IncidentResponse;
import com.network_monitor.portal_service.model.dto.response.ThresholdRuleResponse;
import com.network_monitor.portal_service.model.entity.Device;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;

public interface IncidentService {

    void createOrEscalateIncident(
            Device device,
            ThresholdRuleResponse rule,
            String highestSeverity,
            double appliedLimit,
            double peakValue,
            int breachCount,
            JsonNode breachSamples,
            OffsetDateTime timestamp
    );

    void resolveOpenIncident(Long deviceId, String metricType, OffsetDateTime resolvedAt);

    void autoResolvePreviousDayIncidents();

    Page<IncidentResponse> getIncidentsByMetricType(String metricType, Long deviceId, SearchRequest request);

    Page<DeviceIncidentMetricSummaryResponse> getIncidentsByDevice(Long deviceId, SearchRequest request);
}
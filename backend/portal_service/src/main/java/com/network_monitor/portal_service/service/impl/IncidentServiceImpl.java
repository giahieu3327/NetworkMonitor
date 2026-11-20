package com.network_monitor.portal_service.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.portal_service.mapper.IncidentMapper;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.DeviceIncidentMetricSummaryResponse;
import com.network_monitor.portal_service.model.dto.response.IncidentResponse;
import com.network_monitor.portal_service.model.dto.response.ThresholdRuleResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.Incident;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.IncidentRepository;
import com.network_monitor.portal_service.service.IncidentService;
import com.network_monitor.portal_service.util.SearchRequestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidentRepository;

    private final DeviceRepository deviceRepository;

    private final IncidentMapper incidentMapper;

    private final ObjectMapper objectMapper;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    @Transactional
    public void createOrEscalateIncident(
            Device device,
            ThresholdRuleResponse rule,
            String highestSeverity,
            double appliedLimit,
            double peakValue,
            int breachCount,
            JsonNode breachSamples,
            OffsetDateTime timestamp
    ) {
        String metricType = rule.getMetricType();

        String title = String.format(
                "[%s] %s - %s breached threshold",
                highestSeverity,
                device.getDeviceName(),
                metricType
        );

        String description = String.format(
                "Metric '%s' breached %s limit (%.2f) for %d consecutive checks. Peak value: %.2f.",
                metricType,
                highestSeverity,
                appliedLimit,
                breachCount,
                peakValue
        );

        Optional<Incident> openOpt = incidentRepository
                .findFirstByDeviceIdAndMetricTypeAndStatusOrderByCreatedAtDesc(
                        device.getId(),
                        metricType,
                        "OPEN"
                );

        if (openOpt.isPresent()) {
            Incident existing = openOpt.get();
            LocalDate existingDate = existing.getCreatedAt().atZoneSameInstant(DEFAULT_ZONE).toLocalDate();
            LocalDate currentDate = timestamp.atZoneSameInstant(DEFAULT_ZONE).toLocalDate();

            if (existingDate.isBefore(currentDate)) {
                OffsetDateTime startOfCurrentDay = currentDate.atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();
                existing.setStatus("RESOLVED");
                existing.setResolvedAt(startOfCurrentDay);
                existing.setUpdatedAt(startOfCurrentDay);
                incidentRepository.saveAndFlush(existing);
                openOpt = Optional.empty();

                log.info(
                        "Incident AUTO-RESOLVED on day rollover - ID: {}, Device: {}, Metric: '{}'",
                        existing.getId(),
                        device.getDeviceName(),
                        metricType
                );
            }
        }

        if (openOpt.isEmpty()) {
            Incident newIncident = Incident.builder()
                    .device(device)
                    .metricType(metricType)
                    .severity(highestSeverity)
                    .title(title)
                    .description(description)
                    .peakValue(peakValue)
                    .thresholdLimit(appliedLimit)
                    .breachCount(breachCount)
                    .breachSamples(breachSamples)
                    .status("OPEN")
                    .createdAt(timestamp)
                    .updatedAt(timestamp)
                    .build();

            incidentRepository.saveAndFlush(newIncident);

            log.warn(
                    "Incident CREATED [{}] - Device: {}, Metric: '{}', Peak: {}, Breaches: {}",
                    highestSeverity,
                    device.getDeviceName(),
                    metricType,
                    peakValue,
                    breachCount
            );
        } else {
            Incident existing = openOpt.get();
            boolean escalated = "WARNING".equals(existing.getSeverity())
                    && "CRITICAL".equals(highestSeverity);

            existing.setSeverity(highestSeverity);
            existing.setTitle(title);
            existing.setDescription(description);
            existing.setThresholdLimit(appliedLimit);
            existing.setPeakValue(peakValue);
            existing.setBreachCount(breachCount);
            existing.setBreachSamples(breachSamples);
            existing.setUpdatedAt(timestamp);

            incidentRepository.saveAndFlush(existing);

            if (escalated) {
                log.warn(
                        "Incident ESCALATED to CRITICAL - ID: {}, Device: {}, Metric: '{}', New Peak: {}",
                        existing.getId(),
                        device.getDeviceName(),
                        metricType,
                        peakValue
                );
            }
        }
    }

    @Override
    @Transactional
    public void resolveOpenIncident(Long deviceId, String metricType, OffsetDateTime resolvedAt) {
        incidentRepository
                .findFirstByDeviceIdAndMetricTypeAndStatusOrderByCreatedAtDesc(deviceId, metricType, "OPEN")
                .ifPresent(openIncident -> {
                    openIncident.setStatus("RESOLVED");
                    openIncident.setResolvedAt(resolvedAt);
                    openIncident.setUpdatedAt(resolvedAt);
                    incidentRepository.saveAndFlush(openIncident);

                    log.info(
                            "Incident RESOLVED - ID: {}, Device ID: {}, Metric: '{}', Peak Value: {}, Total Breaches: {}",
                            openIncident.getId(),
                            deviceId,
                            metricType,
                            openIncident.getPeakValue(),
                            openIncident.getBreachCount()
                    );
                });
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void autoResolvePreviousDayIncidents() {
        OffsetDateTime startOfToday = LocalDate.now(DEFAULT_ZONE)
                .atStartOfDay(DEFAULT_ZONE)
                .toOffsetDateTime();

        List<Incident> staleOpenIncidents = incidentRepository
                .findAllByStatusAndCreatedAtBefore("OPEN", startOfToday);

        if (staleOpenIncidents.isEmpty()) {
            return;
        }

        for (Incident incident : staleOpenIncidents) {
            incident.setStatus("RESOLVED");
            incident.setResolvedAt(startOfToday);
            incident.setUpdatedAt(startOfToday);
        }

        incidentRepository.saveAllAndFlush(staleOpenIncidents);

        log.info("Auto-resolved {} open incidents from previous day at {}", staleOpenIncidents.size(), startOfToday);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidentsByMetricType(String metricType, Long deviceId, SearchRequest request) {
        if (metricType == null || metricType.isBlank()) {
            throw new IllegalArgumentException("Metric type is required");
        }

        if (deviceId != null && !deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException("Device not found: " + deviceId);
        }

        String normalizedMetric = metricType.trim().toLowerCase(Locale.ROOT);
        SearchRequest search = request != null ? request : new SearchRequest();

        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        OffsetDateTime start = today.atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();
        OffsetDateTime end = today.plusDays(1).atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();

        Pageable pageable = createPageable(search, "createdAt");
        String keyword = SearchRequestUtils.normalizeKeyword(search);
        String sortBy = SearchRequestUtils.normalizeSortBy(search, "createdAt");
        String direction = SearchRequestUtils.normalizeDirection(search);

        List<IncidentResponse> responses = incidentMapper.searchIncidentsByMetricType(
                normalizedMetric,
                deviceId,
                start,
                end,
                keyword,
                pageable.getOffset(),
                pageable.getPageSize(),
                sortBy,
                direction
        );

        for (IncidentResponse item : responses) {
            if (item.getBreachSamples() instanceof String rawJson && !rawJson.isBlank()) {
                try {
                    item.setBreachSamples(objectMapper.readValue(rawJson, new TypeReference<>() {}));
                } catch (Exception e) {
                    log.warn("Failed to parse breachSamples JSON for incident ID: {}", item.getId(), e);
                }
            }
        }

        long total = incidentMapper.countIncidentsByMetricType(
                normalizedMetric,
                deviceId,
                start,
                end,
                keyword
        );

        return new PageImpl<>(responses, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeviceIncidentMetricSummaryResponse> getIncidentsByDevice(Long deviceId, SearchRequest request) {
        if (deviceId == null) {
            throw new IllegalArgumentException("Device ID is required");
        }

        if (!deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException("Device not found: " + deviceId);
        }

        SearchRequest search = request != null ? request : new SearchRequest();

        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        OffsetDateTime start = today.atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();
        OffsetDateTime end = today.plusDays(1).atStartOfDay(DEFAULT_ZONE).toOffsetDateTime();

        Pageable pageable = createPageable(search, "metricType");
        String keyword = SearchRequestUtils.normalizeKeyword(search);
        String sortBy = SearchRequestUtils.normalizeSortBy(search, "metricType");
        String direction = SearchRequestUtils.normalizeDirection(search);

        List<DeviceIncidentMetricSummaryResponse> summaries = incidentMapper.summarizeIncidentsByDevice(
                deviceId,
                start,
                end,
                keyword,
                pageable.getOffset(),
                pageable.getPageSize(),
                sortBy,
                direction
        );

        for (DeviceIncidentMetricSummaryResponse summary : summaries) {
            if (summary.getAvgPeakValue() != null) {
                summary.setAvgPeakValue(Math.round(summary.getAvgPeakValue() * 100.0) / 100.0);
            }
        }

        long total = incidentMapper.countIncidentMetricsByDevice(
                deviceId,
                start,
                end,
                keyword
        );

        return new PageImpl<>(summaries, pageable, total);
    }

    private Pageable createPageable(SearchRequest request, String defaultSortBy) {
        int page = SearchRequestUtils.normalizePage(request);
        int size = SearchRequestUtils.normalizeSize(request);
        String sortBy = SearchRequestUtils.normalizeSortBy(request, defaultSortBy);
        String direction = SearchRequestUtils.normalizeDirection(request);

        return SearchRequestUtils.toPageable(page, size, sortBy, direction);
    }
}
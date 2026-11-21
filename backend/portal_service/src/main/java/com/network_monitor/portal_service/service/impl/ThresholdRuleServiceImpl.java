package com.network_monitor.portal_service.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.portal_service.mapper.ThresholdRuleMapper;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.request.ThresholdRuleRequest;
import com.network_monitor.portal_service.model.dto.response.AllowedMetricResponse;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.ThresholdRuleResponse;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.ThresholdRule;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.ThresholdRuleRepository;
import com.network_monitor.portal_service.service.IncidentService;
import com.network_monitor.portal_service.service.ThresholdRuleService;
import com.network_monitor.portal_service.util.SearchRequestUtils;
import com.network_monitor.portal_service.workflow.ApprovalWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdRuleServiceImpl implements ThresholdRuleService {

    private final ThresholdRuleRepository thresholdRuleRepository;

    private final DeviceRepository deviceRepository;

    private final ThresholdRuleMapper thresholdRuleMapper;

    private final StringRedisTemplate redisTemplate;

    private final IncidentService incidentService;

    private final ObjectMapper objectMapper;

    private final ApprovalWorkflowService approvalWorkflowService;

    private static final String ENTITY_TYPE = "THRESHOLD_RULE";

    private static final String ADMIN_USERNAME = "admin";

    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";

    private static final String STREAK_REDIS_PREFIX =
            "network-monitor:threshold:streak:v2:";

    private static final Set<String> ALLOWED_METRICS = Set.of(
            "cpu_usage",
            "core_usage",
            "core_count",
            "ram_usage_percent",
            "ram_usage",
            "ram_free",
            "ram_capacity"
    );

    private static final Set<String> PERCENT_METRICS = Set.of(
            "cpu_usage",
            "core_usage",
            "ram_usage_percent"
    );

    private static final Set<String> LOWER_IS_WORSE_METRICS = Set.of(
            "ram_free"
    );

    @Override
    public ApiResponse<List<AllowedMetricResponse>> getAllowedMetrics(Long deviceId) {
        if (deviceId == null) {
            return ApiResponse.error("Device ID is required");
        }

        if (!deviceRepository.existsById(deviceId)) {
            return ApiResponse.error("Device not found: " + deviceId);
        }

        Set<String> availableMetrics = getDeviceMetricsFromRedis(deviceId);

        List<AllowedMetricResponse> metrics = availableMetrics.stream()
                .sorted()
                .map(metric -> {
                    String baseMetric = extractBaseMetric(metric);
                    boolean isPercent = PERCENT_METRICS.contains(baseMetric);
                    boolean isLowerWorse = LOWER_IS_WORSE_METRICS.contains(baseMetric);

                    String unit = isPercent
                            ? "%"
                            : ("core_count".equals(baseMetric) ? "cores" : "bytes");

                    return AllowedMetricResponse.builder()
                            .metricType(metric)
                            .unit(unit)
                            .minValue(0.0)
                            .maxValue(isPercent ? 100.0 : null)
                            .conditionDirection(isLowerWorse ? "LESS_THAN" : "GREATER_THAN")
                            .build();
                })
                .toList();

        return ApiResponse.success("Allowed metrics retrieved successfully", metrics);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<ThresholdRuleResponse> getRuleById(Long id) {
        try {
            if (id == null) {
                return ApiResponse.error("Rule ID is required");
            }

            ThresholdRule rule = thresholdRuleRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Threshold rule not found: " + id
                    ));

            return ApiResponse.success(
                    "Threshold rule retrieved successfully",
                    toResponse(rule)
            );
        } catch (Exception e) {
            log.error("Failed to get threshold rule. ID: {}", id, e);
            return ApiResponse.error("Failed to get threshold rule: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ThresholdRuleResponse> getRulesByDevice(Long deviceId, SearchRequest request) {
        SearchRequest search = request != null
                ? request
                : new SearchRequest();

        int page = SearchRequestUtils.normalizePage(search);
        int size = SearchRequestUtils.normalizeSize(search);
        String keyword = SearchRequestUtils.normalizeKeyword(search);

        String sortBy = SearchRequestUtils.normalizeSortBy(
                search,
                "createdAt"
        );

        String direction =
                SearchRequestUtils.normalizeDirection(search);

        Pageable pageable = SearchRequestUtils.toPageable(
                page,
                size,
                sortBy,
                direction
        );

        List<ThresholdRule> rules =
                thresholdRuleMapper.findByDeviceId(
                        deviceId,
                        keyword,
                        pageable.getOffset(),
                        pageable.getPageSize(),
                        sortBy,
                        direction
                );

        long total = thresholdRuleMapper.countByDeviceId(
                deviceId,
                keyword
        );

        List<ThresholdRuleResponse> responses = rules.stream()
                .map(this::toResponse)
                .toList();

        return new PageImpl<>(responses, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThresholdRuleResponse> getEnabledRulesByDevice(Long deviceId) {
        if (deviceId == null) {
            return List.of();
        }

        return thresholdRuleRepository.findByDeviceIdAndIsEnabledTrue(deviceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ApiResponse<ThresholdRuleResponse> insertRule(ThresholdRuleRequest request) {
        try {
            if (request == null) {
                return ApiResponse.error("Invalid threshold rule request");
            }

            if (request.getDeviceId() == null) {
                return ApiResponse.error("Device ID is required");
            }

            String normalizedMetric = normalizeMetricKey(request.getMetricType());
            String validationError = validateRuleRequest(request, normalizedMetric);
            if (validationError != null) {
                return ApiResponse.error(validationError);
            }

            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Device not found: " + request.getDeviceId()
                    ));

            Set<String> deviceMetrics = getDeviceMetricsFromRedis(device.getId());
            if (!deviceMetrics.isEmpty() && !deviceMetrics.contains(normalizedMetric)) {
                return ApiResponse.error(
                        "Metric '" + normalizedMetric + "' is not available for device " + device.getId()
                );
            }

            boolean duplicated = thresholdRuleRepository.existsByDeviceIdAndMetricType(
                    device.getId(),
                    normalizedMetric
            );

            if (duplicated) {
                return ApiResponse.error(
                        "Threshold rule already exists for device " + device.getId()
                                + " and metric '" + normalizedMetric + "'"
                );
            }

            ThresholdRuleRequest normalizedPayload = ThresholdRuleRequest.builder()
                    .deviceId(device.getId())
                    .metricType(normalizedMetric)
                    .warningLimit(request.getWarningLimit())
                    .criticalLimit(request.getCriticalLimit())
                    .consecutiveOccurrences(
                            request.getConsecutiveOccurrences() != null
                                    ? request.getConsecutiveOccurrences()
                                    : 3
                    )
                    .isEnabled(
                            request.getIsEnabled() != null
                                    ? request.getIsEnabled()
                                    : true
                    )
                    .build();

            if (isSuperAdminAccount()) {
                ThresholdRule saved = insertRuleDirectly(device, normalizedPayload);
                return ApiResponse.success(
                        "Tạo ngưỡng cảnh báo thành công",
                        toResponse(saved)
                );
            }

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "CREATE",
                    null,
                    device.getId(),
                    "Tạo ngưỡng cảnh báo '" + normalizedMetric + "' cho thiết bị ID="
                            + device.getId() + " (" + device.getDeviceName() + ")",
                    null,
                    normalizedPayload
            );

            return ApiResponse.success(
                    "Đã gửi yêu cầu tạo ngưỡng cảnh báo (Ticket #" + ticket.getId() + "), đang chờ phê duyệt",
                    null
            );
        } catch (Exception e) {
            log.error("Failed to create threshold rule", e);
            return ApiResponse.error("Failed to create threshold rule: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Map<String, Object>> createRulesFromExcel(Long deviceId, MultipartFile file) {
        if (deviceId == null) {
            return ApiResponse.error("Device ID is required");
        }

        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            return ApiResponse.error("Không tìm thấy thiết bị");
        }

        if (file == null || file.isEmpty()) {
            return ApiResponse.error("File Excel không được để trống");
        }

        boolean bypassApproval = isSuperAdminAccount();

        int totalRows = 0;
        int validCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        List<ThresholdRuleRequest> validPayloads = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        Set<String> seenMetricsInFile = new HashSet<>();
        Set<String> deviceMetrics = getDeviceMetricsFromRedis(deviceId);

        try (InputStream input = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(input)) {

            if (workbook.getNumberOfSheets() == 0) {
                return ApiResponse.error("File Excel không có sheet dữ liệu");
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (isEmptyRow(row, formatter, 5)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;

                try {
                    ThresholdRuleRequest request = readRequestFromExcel(row, formatter);
                    String normalizedMetric = normalizeMetricKey(request.getMetricType());
                    String validationError = validateRuleRequest(request, normalizedMetric);
                    if (validationError != null) {
                        throw new IllegalArgumentException(validationError);
                    }

                    if (!deviceMetrics.isEmpty() && !deviceMetrics.contains(normalizedMetric)) {
                        throw new IllegalArgumentException(
                                "Metric '" + normalizedMetric + "' is not available for device " + deviceId
                        );
                    }

                    if (!seenMetricsInFile.add(normalizedMetric)) {
                        throw new IllegalArgumentException(
                                "Trùng metric '" + normalizedMetric + "' trong cùng file Excel"
                        );
                    }

                    if (thresholdRuleRepository.existsByDeviceIdAndMetricType(deviceId, normalizedMetric)) {
                        throw new IllegalArgumentException(
                                "Threshold rule already exists for device " + deviceId
                                        + " and metric '" + normalizedMetric + "'"
                        );
                    }

                    request.setDeviceId(deviceId);
                    request.setMetricType(normalizedMetric);

                    if (bypassApproval) {
                        insertRuleDirectly(device, request);
                    } else {
                        validPayloads.add(request);
                    }

                    validCount++;
                } catch (Exception e) {
                    failedCount++;
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("row", i + 1);
                    error.put("message", getErrorMessage(e));
                    errors.add(error);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalRows", totalRows);
            result.put("successCount", validCount);
            result.put("failedCount", failedCount);
            result.put("skippedCount", skippedCount);
            result.put("errors", errors);

            if (bypassApproval) {
                if (validCount == 0) {
                    return ApiResponse.error("Không có dòng dữ liệu hợp lệ nào trong file Excel");
                }
                return ApiResponse.success(
                        "Import " + validCount + " ngưỡng cảnh báo từ Excel thành công",
                        result
                );
            }

            Long ticketId = null;
            if (!validPayloads.isEmpty()) {
                ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                        ENTITY_TYPE,
                        "BULK_IMPORT",
                        null,
                        deviceId,
                        "Import " + validPayloads.size() + " ngưỡng cảnh báo từ Excel cho thiết bị ID="
                                + deviceId + " (" + device.getDeviceName() + ")",
                        null,
                        validPayloads
                );
                ticketId = ticket.getId();
            }

            result.put("ticketId", ticketId);

            if (validPayloads.isEmpty()) {
                return ApiResponse.error("Không có dòng dữ liệu hợp lệ nào trong file Excel");
            }

            return ApiResponse.success(
                    "Đã gửi yêu cầu import " + validPayloads.size()
                            + " ngưỡng cảnh báo (Ticket #" + ticketId + "), đang chờ phê duyệt",
                    result
            );
        } catch (Exception e) {
            log.error("Import threshold rules from Excel failed: deviceId={}", deviceId, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> updateRule(Long id, ThresholdRuleRequest request) {
        try {
            if (id == null) {
                return ApiResponse.error("Rule ID is required");
            }

            if (request == null) {
                return ApiResponse.error("Invalid threshold rule request");
            }

            ThresholdRule existingRule = thresholdRuleRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Threshold rule not found: " + id
                    ));

            Long oldDeviceId = existingRule.getDevice().getId();
            String oldMetricType = existingRule.getMetricType();

            Long targetDeviceId = request.getDeviceId() != null
                    ? request.getDeviceId()
                    : oldDeviceId;

            String normalizedMetric = request.getMetricType() != null
                    ? normalizeMetricKey(request.getMetricType())
                    : oldMetricType;

            String validationError = validateRuleRequest(request, normalizedMetric);
            if (validationError != null) {
                return ApiResponse.error(validationError);
            }

            Device device = existingRule.getDevice();
            if (!device.getId().equals(targetDeviceId)) {
                device = deviceRepository.findById(targetDeviceId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Device not found: " + targetDeviceId
                        ));
            }

            Set<String> deviceMetrics = getDeviceMetricsFromRedis(device.getId());
            if (!deviceMetrics.isEmpty() && !deviceMetrics.contains(normalizedMetric)) {
                return ApiResponse.error(
                        "Metric '" + normalizedMetric + "' is not available for device " + device.getId()
                );
            }

            boolean duplicated = thresholdRuleRepository.existsByDeviceIdAndMetricTypeAndIdNot(
                    device.getId(),
                    normalizedMetric,
                    id
            );

            if (duplicated) {
                return ApiResponse.error(
                        "Another threshold rule already exists for device " + device.getId()
                                + " and metric '" + normalizedMetric + "'"
                );
            }

            ThresholdRuleRequest normalizedPayload = ThresholdRuleRequest.builder()
                    .deviceId(device.getId())
                    .metricType(normalizedMetric)
                    .warningLimit(request.getWarningLimit())
                    .criticalLimit(request.getCriticalLimit())
                    .consecutiveOccurrences(
                            request.getConsecutiveOccurrences() != null
                                    ? request.getConsecutiveOccurrences()
                                    : existingRule.getConsecutiveOccurrences()
                    )
                    .isEnabled(
                            request.getIsEnabled() != null
                                    ? request.getIsEnabled()
                                    : existingRule.getIsEnabled()
                    )
                    .build();

            if (isSuperAdminAccount()) {
                executeUpdateRule(id, normalizedPayload);
                return ApiResponse.success(
                        "Cập nhật ngưỡng cảnh báo thành công",
                        null
                );
            }

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "UPDATE",
                    id,
                    device.getId(),
                    "Cập nhật ngưỡng cảnh báo ID=" + id + " (" + existingRule.getMetricType()
                            + ") trên thiết bị ID=" + device.getId() + " (" + device.getDeviceName() + ")",
                    toResponse(existingRule),
                    normalizedPayload
            );

            return ApiResponse.success(
                    "Đã gửi yêu cầu cập nhật ngưỡng cảnh báo (Ticket #" + ticket.getId() + "), đang chờ phê duyệt",
                    null
            );
        } catch (Exception e) {
            log.error("Failed to update threshold rule. ID: {}", id, e);
            return ApiResponse.error("Failed to update threshold rule: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteRule(Long id) {
        try {
            if (id == null) {
                return ApiResponse.error("Rule ID is required");
            }

            ThresholdRule existingRule = thresholdRuleRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Threshold rule not found: " + id
                    ));

            if (isSuperAdminAccount()) {
                executeDeleteRule(id);
                return ApiResponse.success(
                        "Xóa ngưỡng cảnh báo thành công",
                        null
                );
            }

            Device device = existingRule.getDevice();

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "DELETE",
                    id,
                    device.getId(),
                    "Xóa ngưỡng cảnh báo ID=" + id + " (" + existingRule.getMetricType()
                            + ") trên thiết bị ID=" + device.getId() + " (" + device.getDeviceName() + ")",
                    toResponse(existingRule),
                    null
            );

            return ApiResponse.success(
                    "Đã gửi yêu cầu xóa ngưỡng cảnh báo (Ticket #" + ticket.getId() + "), đang chờ phê duyệt",
                    null
            );
        } catch (Exception e) {
            log.error("Failed to delete threshold rule. ID: {}", id, e);
            return ApiResponse.error("Failed to delete threshold rule: " + e.getMessage());
        }
    }

    private boolean isSuperAdminAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        String username = null;
        boolean hasSuperAdminRole = false;

        if (auth.getPrincipal() instanceof Jwt jwt) {
            username = jwt.getClaimAsString("preferred_username");
            if (username == null || username.isBlank()) {
                username = jwt.getSubject();
            }

            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if (SUPER_ADMIN_ROLE.equals(String.valueOf(role))) {
                        hasSuperAdminRole = true;
                        break;
                    }
                }
            }
        } else {
            username = auth.getName();
        }

        if (!hasSuperAdminRole && auth.getAuthorities() != null) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if (SUPER_ADMIN_ROLE.equals(authority.getAuthority())) {
                    hasSuperAdminRole = true;
                    break;
                }
            }
        }

        return ADMIN_USERNAME.equalsIgnoreCase(username) && hasSuperAdminRole;
    }

    private ThresholdRule insertRuleDirectly(Device device, ThresholdRuleRequest request) {
        OffsetDateTime now = OffsetDateTime.now();

        ThresholdRule rule = ThresholdRule.builder()
                .device(device)
                .metricType(request.getMetricType())
                .warningLimit(request.getWarningLimit())
                .criticalLimit(request.getCriticalLimit())
                .consecutiveOccurrences(
                        request.getConsecutiveOccurrences() != null
                                ? request.getConsecutiveOccurrences()
                                : 3
                )
                .isEnabled(
                        request.getIsEnabled() != null
                                ? request.getIsEnabled()
                                : true
                )
                .createdAt(now)
                .updatedAt(now)
                .build();

        return thresholdRuleRepository.saveAndFlush(rule);
    }

    @Transactional
    public void executeCreateRule(ThresholdRuleRequest request) {
        if (request == null || request.getDeviceId() == null) {
            throw new IllegalArgumentException("Device ID is required");
        }

        String normalizedMetric = normalizeMetricKey(request.getMetricType());
        String validationError = validateRuleRequest(request, normalizedMetric);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        Device device = deviceRepository.findById(request.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Device not found: " + request.getDeviceId()
                ));

        Set<String> deviceMetrics = getDeviceMetricsFromRedis(device.getId());
        if (!deviceMetrics.isEmpty() && !deviceMetrics.contains(normalizedMetric)) {
            throw new IllegalArgumentException(
                    "Metric '" + normalizedMetric + "' is not available for device " + device.getId()
            );
        }

        if (thresholdRuleRepository.existsByDeviceIdAndMetricType(device.getId(), normalizedMetric)) {
            throw new IllegalArgumentException(
                    "Threshold rule already exists for device " + device.getId()
                            + " and metric '" + normalizedMetric + "'"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        ThresholdRule rule = ThresholdRule.builder()
                .device(device)
                .metricType(normalizedMetric)
                .warningLimit(request.getWarningLimit())
                .criticalLimit(request.getCriticalLimit())
                .consecutiveOccurrences(
                        request.getConsecutiveOccurrences() != null
                                ? request.getConsecutiveOccurrences()
                                : 3
                )
                .isEnabled(
                        request.getIsEnabled() != null
                                ? request.getIsEnabled()
                                : true
                )
                .createdAt(now)
                .updatedAt(now)
                .build();

        ThresholdRule saved = thresholdRuleRepository.saveAndFlush(rule);
        log.info(
                "Threshold rule created after approval. ID: {}, Device ID: {}, Metric: {}",
                saved.getId(),
                device.getId(),
                normalizedMetric
        );
    }

    @Transactional
    public void executeBulkImportRules(Long deviceId, List<ThresholdRuleRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Danh sách ngưỡng cảnh báo import trống");
        }

        for (ThresholdRuleRequest item : requests) {
            if (deviceId != null && item.getDeviceId() == null) {
                item.setDeviceId(deviceId);
            }
            executeCreateRule(item);
        }
    }

    @Transactional
    public void executeUpdateRule(Long id, ThresholdRuleRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("Rule ID is required");
        }

        if (request == null) {
            throw new IllegalArgumentException("Invalid threshold rule request");
        }

        ThresholdRule existingRule = thresholdRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Threshold rule not found: " + id
                ));

        Boolean wasEnabled = existingRule.getIsEnabled();
        Long oldDeviceId = existingRule.getDevice().getId();
        String oldMetricType = existingRule.getMetricType();

        Long targetDeviceId = request.getDeviceId() != null
                ? request.getDeviceId()
                : oldDeviceId;

        String normalizedMetric = request.getMetricType() != null
                ? normalizeMetricKey(request.getMetricType())
                : oldMetricType;

        String validationError = validateRuleRequest(request, normalizedMetric);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        Device device = existingRule.getDevice();
        if (!device.getId().equals(targetDeviceId)) {
            device = deviceRepository.findById(targetDeviceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Device not found: " + targetDeviceId
                    ));
        }

        Set<String> deviceMetrics = getDeviceMetricsFromRedis(device.getId());
        if (!deviceMetrics.isEmpty() && !deviceMetrics.contains(normalizedMetric)) {
            throw new IllegalArgumentException(
                    "Metric '" + normalizedMetric + "' is not available for device " + device.getId()
            );
        }

        boolean duplicated = thresholdRuleRepository.existsByDeviceIdAndMetricTypeAndIdNot(
                device.getId(),
                normalizedMetric,
                id
        );

        if (duplicated) {
            throw new IllegalArgumentException(
                    "Another threshold rule already exists for device " + device.getId()
                            + " and metric '" + normalizedMetric + "'"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        existingRule.setDevice(device);
        existingRule.setMetricType(normalizedMetric);
        existingRule.setWarningLimit(request.getWarningLimit());
        existingRule.setCriticalLimit(request.getCriticalLimit());

        if (request.getConsecutiveOccurrences() != null) {
            existingRule.setConsecutiveOccurrences(request.getConsecutiveOccurrences());
        }

        if (request.getIsEnabled() != null) {
            existingRule.setIsEnabled(request.getIsEnabled());
        }

        existingRule.setUpdatedAt(now);

        ThresholdRule updated = thresholdRuleRepository.saveAndFlush(existingRule);
        redisTemplate.delete(STREAK_REDIS_PREFIX + id);

        boolean isEnabledChanged = !Objects.equals(wasEnabled, updated.getIsEnabled());
        boolean metricTypeChanged = !oldMetricType.equals(normalizedMetric);
        boolean deviceChanged = !oldDeviceId.equals(device.getId());

        if (isEnabledChanged || metricTypeChanged || deviceChanged) {
            incidentService.resolveOpenIncident(oldDeviceId, oldMetricType, now);
        }

        log.info(
                "Threshold rule updated. ID: {}, Device ID: {}, Metric: {}",
                updated.getId(),
                device.getId(),
                normalizedMetric
        );
    }

    @Transactional
    public void executeDeleteRule(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Rule ID is required");
        }

        ThresholdRule existingRule = thresholdRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Threshold rule not found: " + id
                ));

        Boolean wasEnabled = existingRule.getIsEnabled();
        Long deviceId = existingRule.getDevice().getId();
        String metricType = existingRule.getMetricType();

        thresholdRuleRepository.delete(existingRule);
        thresholdRuleRepository.flush();
        redisTemplate.delete(STREAK_REDIS_PREFIX + id);

        if (Boolean.TRUE.equals(wasEnabled)) {
            incidentService.resolveOpenIncident(deviceId, metricType, OffsetDateTime.now());
        }

        log.info("Threshold rule deleted. ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportRules(Long deviceId) throws IOException {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException("Không tìm thấy thiết bị");
        }

        List<ThresholdRule> rules = thresholdRuleRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Threshold Rules");

            String[] headers = {
                    "ID",
                    "Device ID",
                    "Device Name",
                    "Metric Type",
                    "Warning Limit",
                    "Critical Limit",
                    "Consecutive Occurrences",
                    "Is Enabled",
                    "Created At",
                    "Updated At"
            };

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (ThresholdRule rule : rules) {
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(rule.getId() != null ? rule.getId() : 0);
                row.createCell(1).setCellValue(
                        rule.getDevice() != null && rule.getDevice().getId() != null
                                ? rule.getDevice().getId()
                                : 0
                );
                row.createCell(2).setCellValue(
                        rule.getDevice() != null ? getSafeString(rule.getDevice().getDeviceName()) : ""
                );
                row.createCell(3).setCellValue(getSafeString(rule.getMetricType()));

                if (rule.getWarningLimit() != null) {
                    row.createCell(4).setCellValue(rule.getWarningLimit());
                } else {
                    row.createCell(4).setCellValue("");
                }

                if (rule.getCriticalLimit() != null) {
                    row.createCell(5).setCellValue(rule.getCriticalLimit());
                } else {
                    row.createCell(5).setCellValue("");
                }

                row.createCell(6).setCellValue(
                        rule.getConsecutiveOccurrences() != null ? rule.getConsecutiveOccurrences() : 3
                );
                row.createCell(7).setCellValue(
                        Boolean.TRUE.equals(rule.getIsEnabled()) ? "Yes" : "No"
                );
                row.createCell(8).setCellValue(
                        rule.getCreatedAt() != null ? rule.getCreatedAt().toString() : ""
                );
                row.createCell(9).setCellValue(
                        rule.getUpdatedAt() != null ? rule.getUpdatedAt().toString() : ""
                );
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) > 50 * 256) {
                    sheet.setColumnWidth(i, 50 * 256);
                }
            }

            sheet.createFreezePane(0, 1);

            if (!rules.isEmpty()) {
                sheet.setAutoFilter(
                        new CellRangeAddress(0, rules.size(), 0, headers.length - 1)
                );
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }

    @Override
    public byte[] downloadRuleImportTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet dataSheet = workbook.createSheet("Threshold Rules");

            String[] headers = {
                    "Metric Type",
                    "Warning Limit",
                    "Critical Limit",
                    "Consecutive Occurrences",
                    "Is Enabled"
            };

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                dataSheet.autoSizeColumn(i);
            }

            Sheet instructionsSheet = workbook.createSheet("Instructions");

            String[][] instructions = {
                    {"Column", "Description"},
                    {
                            "Metric Type",
                            "Loại metric hợp lệ: " + String.join(", ", ALLOWED_METRICS.stream().sorted().toList())
                    },
                    {
                            "Warning Limit",
                            "Ngưỡng cảnh báo Warning (>= 0; từ 0-100 đối với metric %). Bắt buộc nhập ít nhất Warning Limit hoặc Critical Limit"
                    },
                    {
                            "Critical Limit",
                            "Ngưỡng cảnh báo Critical (>= 0; từ 0-100 đối với metric %). Với ram_free thì Critical < Warning, các metric khác Warning < Critical"
                    },
                    {
                            "Consecutive Occurrences",
                            "Số lần vi phạm liên tiếp trước khi tạo sự cố (số nguyên >= 1; để trống mặc định là 3)"
                    },
                    {
                            "Is Enabled",
                            "Yes/No hoặc 1/0; để trống sẽ mặc định Yes"
                    },
                    {
                            "Note",
                            "Dòng đầu tiên của sheet Threshold Rules là tiêu đề; không sửa thứ tự cột. Device ID được xác định từ URL import."
                    }
            };

            for (int i = 0; i < instructions.length; i++) {
                Row row = instructionsSheet.createRow(i);
                for (int j = 0; j < instructions[i].length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(instructions[i][j]);
                    if (i == 0) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }

            instructionsSheet.setColumnWidth(0, 26 * 256);
            instructionsSheet.setColumnWidth(1, 110 * 256);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private Set<String> getDeviceMetricsFromRedis(Long deviceId) {
        Set<String> keys = redisTemplate.keys("network-monitor:snmp:metric:" + deviceId + ":*");
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }

        Set<String> resolvedMetrics = new LinkedHashSet<>();

        for (String key : keys) {
            int lastColon = key.lastIndexOf(':');
            String rawMetricName = lastColon >= 0 ? key.substring(lastColon + 1) : key;
            String baseMetric = normalizeMetricKey(rawMetricName);

            if (!ALLOWED_METRICS.contains(baseMetric)) {
                continue;
            }

            String payload = readLatestPayloadFromRedis(key);
            if (payload == null || payload.isBlank()) {
                resolvedMetrics.add(baseMetric);
                continue;
            }

            try {
                JsonNode rootNode = objectMapper.readTree(payload);
                JsonNode valueNode = rootNode.get("value");
                if (valueNode != null && valueNode.isObject() && !valueNode.isEmpty()) {
                    expandNestedMetricKeys(baseMetric, valueNode, resolvedMetrics);
                } else {
                    resolvedMetrics.add(baseMetric);
                }
            } catch (Exception e) {
                log.warn("Failed to parse metric payload from Redis for key: {}", key, e);
                resolvedMetrics.add(baseMetric);
            }
        }

        return resolvedMetrics;
    }

    private String readLatestPayloadFromRedis(String key) {
        try {
            DataType type = redisTemplate.type(key);
            if (type == null || type == DataType.NONE) {
                return null;
            }

            return switch (type) {
                case STRING -> redisTemplate.opsForValue().get(key);
                case LIST -> redisTemplate.opsForList().index(key, -1);
                case ZSET -> {
                    Set<String> latest = redisTemplate.opsForZSet().reverseRange(key, 0, 0);
                    yield (latest != null && !latest.isEmpty()) ? latest.iterator().next() : null;
                }
                case HASH -> {
                    Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                    yield entries.isEmpty() ? null : objectMapper.writeValueAsString(entries);
                }
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Failed to read Redis key {} due to type/execution error: {}", key, e.getMessage());
            return null;
        }
    }

    private void expandNestedMetricKeys(String prefix, JsonNode node, Set<String> target) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String childKey = sanitizeKeySegment(entry.getKey());
            String nextPrefix = prefix + "-" + childKey;
            JsonNode childValue = entry.getValue();

            if (childValue != null && childValue.isObject() && !childValue.isEmpty()) {
                expandNestedMetricKeys(nextPrefix, childValue, target);
            } else {
                target.add(nextPrefix);
            }
        }
    }

    private String sanitizeKeySegment(String segment) {
        if (segment == null) {
            return "";
        }
        return segment.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private String extractBaseMetric(String metric) {
        if (metric == null) {
            return "";
        }
        int dashIndex = metric.indexOf('-');
        return dashIndex >= 0 ? metric.substring(0, dashIndex) : metric;
    }

    private ThresholdRuleRequest readRequestFromExcel(Row row, DataFormatter formatter) {
        String metricType = getCell(row, 0, formatter);
        String warningValue = getCell(row, 1, formatter);
        String criticalValue = getCell(row, 2, formatter);
        String consecutiveValue = getCell(row, 3, formatter);
        String enabledValue = getCell(row, 4, formatter);

        if (metricType.isBlank()) {
            throw new IllegalArgumentException("Metric Type không được để trống");
        }

        Double warningLimit = parseOptionalDouble(warningValue, "Warning Limit");
        Double criticalLimit = parseOptionalDouble(criticalValue, "Critical Limit");

        Integer consecutiveOccurrences = 3;
        if (!consecutiveValue.isBlank()) {
            try {
                consecutiveOccurrences = Integer.parseInt(consecutiveValue);
                if (consecutiveOccurrences < 1) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Consecutive Occurrences phải là số nguyên >= 1");
            }
        }

        Boolean isEnabled;
        if (enabledValue.isBlank()
                || enabledValue.equalsIgnoreCase("Yes")
                || enabledValue.equals("1")) {
            isEnabled = Boolean.TRUE;
        } else if (enabledValue.equalsIgnoreCase("No")
                || enabledValue.equals("0")) {
            isEnabled = Boolean.FALSE;
        } else {
            throw new IllegalArgumentException("Cột Is Enabled chỉ chấp nhận Yes/No hoặc 1/0");
        }

        return ThresholdRuleRequest.builder()
                .metricType(metricType)
                .warningLimit(warningLimit)
                .criticalLimit(criticalLimit)
                .consecutiveOccurrences(consecutiveOccurrences)
                .isEnabled(isEnabled)
                .build();
    }

    private Double parseOptionalDouble(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            double parsed = Double.parseDouble(rawValue);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " phải là số hợp lệ");
        }
    }

    private String getCell(Row row, int index, DataFormatter formatter) {
        Cell cell = row == null
                ? null
                : row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

        return cell == null
                ? ""
                : formatter.formatCellValue(cell).trim();
    }

    private boolean isEmptyRow(Row row, DataFormatter formatter, int columnCount) {
        if (row == null) {
            return true;
        }

        for (int i = 0; i < columnCount; i++) {
            if (!getCell(row, i, formatter).isBlank()) {
                return false;
            }
        }

        return true;
    }

    private String getSafeString(String value) {
        return value != null ? value : "";
    }

    private String getErrorMessage(Throwable exception) {
        Throwable cause = exception;

        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        return cause.getMessage() != null
                ? cause.getMessage()
                : "Đã xảy ra lỗi không xác định";
    }

    private String validateRuleRequest(ThresholdRuleRequest request, String normalizedMetric) {
        String baseMetric = extractBaseMetric(normalizedMetric);
        if (baseMetric.isBlank() || !ALLOWED_METRICS.contains(baseMetric)) {
            return "Unsupported metric_type. Allowed values: " + ALLOWED_METRICS;
        }

        Double warning = request.getWarningLimit();
        Double critical = request.getCriticalLimit();

        if (warning == null && critical == null) {
            return "At least one of warning_limit or critical_limit must be provided";
        }

        if (warning != null && warning < 0) {
            return "warning_limit must be non-negative";
        }

        if (critical != null && critical < 0) {
            return "critical_limit must be non-negative";
        }

        if (PERCENT_METRICS.contains(baseMetric)) {
            if (warning != null && warning > 100) {
                return "warning_limit for '" + normalizedMetric + "' must be between 0 and 100";
            }
            if (critical != null && critical > 100) {
                return "critical_limit for '" + normalizedMetric + "' must be between 0 and 100";
            }
        }

        if (warning != null && critical != null) {
            if (LOWER_IS_WORSE_METRICS.contains(baseMetric)) {
                if (critical >= warning) {
                    return "For '" + normalizedMetric + "', critical_limit must be less than warning_limit";
                }
            } else {
                if (warning >= critical) {
                    return "For '" + normalizedMetric + "', warning_limit must be less than critical_limit";
                }
            }
        }

        if (request.getConsecutiveOccurrences() != null && request.getConsecutiveOccurrences() < 1) {
            return "consecutive_occurrences must be at least 1";
        }

        return null;
    }

    private String normalizeMetricKey(String metricName) {
        if (metricName == null) {
            return "";
        }

        return metricName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "_")
                .replaceAll("-+", "-")
                .replaceAll("^[_-]+|[_-]+$", "");
    }

    private ThresholdRuleResponse toResponse(ThresholdRule rule) {
        return ThresholdRuleResponse.builder()
                .id(rule.getId())
                .deviceId(rule.getDevice().getId())
                .deviceName(rule.getDevice().getDeviceName())
                .metricType(rule.getMetricType())
                .warningLimit(rule.getWarningLimit())
                .criticalLimit(rule.getCriticalLimit())
                .consecutiveOccurrences(rule.getConsecutiveOccurrences())
                .isEnabled(rule.getIsEnabled())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
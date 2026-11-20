package com.network_monitor.portal_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.network_monitor.portal_service.mapper.MetricHistoryMapper;
import com.network_monitor.portal_service.model.dto.request.MetricRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.MetricHistoryRow;
import com.network_monitor.portal_service.model.dto.response.MetricResponse;
import com.network_monitor.portal_service.model.dto.response.ThresholdRuleResponse;

import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.Metric;

import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.MetricRepository;

import com.network_monitor.portal_service.service.IncidentService;
import com.network_monitor.portal_service.service.MetricService;
import com.network_monitor.portal_service.service.ThresholdRuleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final MetricRepository metricRepository;

    private final DeviceRepository deviceRepository;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final MetricHistoryMapper metricHistoryMapper;

    private final ThresholdRuleService thresholdRuleService;

    private final IncidentService incidentService;

    private static final String REDIS_PREFIX =
            "network-monitor:snmp:metric:";

    private static final int MAX_REDIS_SAMPLES = 15;

    private static final Duration REDIS_TTL =
            Duration.ofMinutes(20);

    private static final String STREAK_REDIS_PREFIX =
            "network-monitor:threshold:streak:v2:";

    private static final Duration STREAK_TTL =
            Duration.ofHours(1);

    private static final ZoneId DEFAULT_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Set<String> LOWER_IS_WORSE_METRICS = Set.of(
            "ram_free"
    );

    private static final Set<String> ALLOWED_METRICS = Set.of(
            "cpu_usage",
            "core_usage",
            "core_count",
            "ram_usage_percent",
            "ram_usage",
            "ram_free",
            "ram_capacity"
    );

    private static final Set<String> POSTGRES_METRICS = Set.of(
            "cpu_usage",
            "ram_usage_percent"
    );

    @Override
    @Transactional
    public ApiResponse<MetricResponse> insertMetric(
            MetricRequest request
    ) {
        try {
            if (request == null || request.getDeviceId() == null) {
                return ApiResponse.error(
                        "Invalid metric request"
                );
            }

            if (request.getMetricData() == null
                    || request.getMetricData().isEmpty()) {
                return ApiResponse.error(
                        "Metric data is empty"
                );
            }

            Long deviceId = request.getDeviceId();

            OffsetDateTime timestamp = request.getTimestamp() != null
                    ? request.getTimestamp()
                    : OffsetDateTime.now();

            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Device not found: " + deviceId
                    ));

            Map<String, Object> allowedData =
                    filterAllowedMetrics(request.getMetricData());

            if (allowedData.isEmpty()) {
                log.warn(
                        "No allowed metrics found. Device ID: {}",
                        deviceId
                );

                return ApiResponse.error(
                        "No supported metric data"
                );
            }

            saveAllMetricsToRedis(
                    deviceId,
                    allowedData,
                    timestamp
            );

            evaluateThresholdRules(
                    device,
                    allowedData,
                    timestamp
            );

            Map<String, Object> postgresData =
                    filterMetricsForPostgres(allowedData);

            if (postgresData.isEmpty()) {
                log.info(
                        "Metrics saved to Redis only. Device ID: {}, data: {}",
                        deviceId,
                        allowedData
                );

                return ApiResponse.success(
                        "Metric saved to Redis successfully",
                        null
                );
            }

            Metric metric = new Metric();
            metric.setDevice(device);
            metric.setMetricData(postgresData);
            metric.setTimestamp(timestamp);

            Metric savedMetric =
                    metricRepository.saveAndFlush(metric);

            MetricResponse response = MetricResponse.builder()
                    .id(savedMetric.getId())
                    .deviceId(deviceId)
                    .deviceName(device.getDeviceName())
                    .metricData(savedMetric.getMetricData())
                    .timestamp(savedMetric.getTimestamp())
                    .build();

            log.info(
                    "Metric saved successfully. Device ID: {}, PostgreSQL: {}",
                    deviceId,
                    postgresData
            );

            return ApiResponse.success(
                    "Metric saved successfully",
                    response
            );

        } catch (Exception e) {
            log.error("Failed to insert metric", e);

            return ApiResponse.error(
                    "Failed to insert metric: " + e.getMessage()
            );
        }
    }

    private Map<String, Object> filterAllowedMetrics(
            Map<String, Object> metricData
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : metricData.entrySet()) {
            String metricName = normalizeMetricKey(entry.getKey());

            if (!ALLOWED_METRICS.contains(metricName)) {
                continue;
            }

            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            result.put(metricName, value);
        }

        return result;
    }

    private void saveAllMetricsToRedis(
            Long deviceId,
            Map<String, Object> metricData,
            OffsetDateTime timestamp
    ) throws JsonProcessingException {
        for (Map.Entry<String, Object> entry : metricData.entrySet()) {
            String metricName = normalizeMetricKey(entry.getKey());

            if (metricName.isBlank()) {
                continue;
            }

            String redisKey = REDIS_PREFIX + deviceId + ":" + metricName;

            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("deviceId", deviceId);
            sample.put("metricName", metricName);
            sample.put("value", entry.getValue());
            sample.put("timestamp", timestamp.toString());

            String json = objectMapper.writeValueAsString(sample);

            redisTemplate.opsForList().leftPush(redisKey, json);
            redisTemplate.opsForList().trim(redisKey, 0, MAX_REDIS_SAMPLES - 1);
            redisTemplate.expire(redisKey, REDIS_TTL);
        }
    }

    private String normalizeMetricKey(String metricName) {
        if (metricName == null) {
            return "";
        }

        return metricName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private Map<String, Object> filterMetricsForPostgres(
            Map<String, Object> metricData
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (String metricName : POSTGRES_METRICS) {
            Object value = metricData.get(metricName);

            if (value == null) {
                continue;
            }

            if ("cpu_usage".equals(metricName)) {
                if (value instanceof Number || value instanceof Map<?, ?>) {
                    result.put(metricName, value);
                }
            } else if (value instanceof Number) {
                result.put(metricName, value);
            }
        }

        return result;
    }

    @Override
    @Transactional
    @Scheduled(
            cron = "0 0 * * * *",
            zone = "Asia/Ho_Chi_Minh"
    )
    public ApiResponse<Integer> deleteExpiredMetrics() {
        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minusHours(24);

            int deletedCount = metricRepository.deleteExpiredMetrics(cutoff);

            log.info("Deleted {} expired metric records", deletedCount);

            return ApiResponse.success(
                    "Expired metrics deleted successfully",
                    deletedCount
            );
        } catch (Exception e) {
            log.error("Failed to delete expired metrics", e);

            return ApiResponse.error(
                    "Failed to delete expired metrics: " + e.getMessage()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getLatestMetrics(
            Long deviceId
    ) {
        try {
            if (deviceId == null) {
                return ApiResponse.error("Device ID is required");
            }

            deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Device not found: " + deviceId
                    ));

            Map<String, Object> result = new LinkedHashMap<>();

            for (String metricName : ALLOWED_METRICS) {
                String redisKey = REDIS_PREFIX + deviceId + ":" + metricName;

                String json = redisTemplate.opsForList().index(redisKey, 0);
                if (json == null) {
                    continue;
                }

                JsonNode sample = objectMapper.readTree(json);
                JsonNode valueNode = sample.get("value");

                if (valueNode == null || valueNode.isNull()) {
                    continue;
                }

                Object value = objectMapper.convertValue(
                        valueNode,
                        Object.class
                );

                Map<String, Object> metric = new LinkedHashMap<>();
                metric.put("value", value);
                metric.put(
                        "timestamp",
                        sample.hasNonNull("timestamp")
                                ? sample.get("timestamp").asText()
                                : null
                );

                result.put(metricName, metric);
            }

            log.info(
                    "Retrieved latest metrics from Redis. Device ID: {}, count: {}",
                    deviceId,
                    result.size()
            );

            return ApiResponse.success(
                    "Latest metrics retrieved successfully",
                    result
            );
        } catch (Exception e) {
            log.error("Failed to get latest metrics. Device ID: {}", deviceId, e);

            return ApiResponse.error(
                    "Failed to get latest metrics: " + e.getMessage()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<MetricResponse>> getCpuHistory(
            Long deviceId
    ) {
        try {
            if (deviceId == null) {
                return ApiResponse.error("Device ID is required");
            }

            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Device not found: " + deviceId
                    ));

            List<MetricHistoryRow> rows =
                    metricHistoryMapper.findCpuHistory(deviceId);

            List<MetricResponse> result = new ArrayList<>();

            for (MetricHistoryRow row : rows) {
                if (row.getMetricValueJson() == null) {
                    continue;
                }

                Object cpuValue = objectMapper.readValue(
                        row.getMetricValueJson(),
                        Object.class
                );

                Map<String, Object> metricData = new LinkedHashMap<>();
                metricData.put("cpu_usage", cpuValue);

                MetricResponse response = MetricResponse.builder()
                        .id(row.getId())
                        .deviceId(deviceId)
                        .deviceName(device.getDeviceName())
                        .metricData(metricData)
                        .timestamp(row.getTimestamp())
                        .build();

                result.add(response);
            }

            Collections.reverse(result);

            log.info(
                    "Retrieved CPU history. Device ID: {}, records: {}",
                    deviceId,
                    result.size()
            );

            return ApiResponse.success(
                    "CPU history retrieved successfully",
                    result
            );
        } catch (Exception e) {
            log.error("Failed to get CPU history. Device ID: {}", deviceId, e);

            return ApiResponse.error(
                    "Failed to get CPU history: " + e.getMessage()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<MetricResponse>> getRamHistory(
            Long deviceId
    ) {
        try {
            if (deviceId == null) {
                return ApiResponse.error("Device ID is required");
            }

            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Device not found: " + deviceId
                    ));

            List<MetricHistoryRow> rows =
                    metricHistoryMapper.findRamHistory(deviceId);

            List<MetricResponse> result = new ArrayList<>();

            for (MetricHistoryRow row : rows) {
                if (row.getMetricValueJson() == null) {
                    continue;
                }

                Object ramValue = objectMapper.readValue(
                        row.getMetricValueJson(),
                        Object.class
                );

                Map<String, Object> metricData = new LinkedHashMap<>();
                metricData.put("ram_usage_percent", ramValue);

                MetricResponse response = MetricResponse.builder()
                        .id(row.getId())
                        .deviceId(deviceId)
                        .deviceName(device.getDeviceName())
                        .metricData(metricData)
                        .timestamp(row.getTimestamp())
                        .build();

                result.add(response);
            }

            Collections.reverse(result);

            log.info(
                    "Retrieved RAM history. Device ID: {}, records: {}",
                    deviceId,
                    result.size()
            );

            return ApiResponse.success(
                    "RAM history retrieved successfully",
                    result
            );
        } catch (Exception e) {
            log.error("Failed to get RAM history. Device ID: {}", deviceId, e);

            return ApiResponse.error(
                    "Failed to get RAM history: " + e.getMessage()
            );
        }
    }

    private void evaluateThresholdRules(
            Device device,
            Map<String, Object> currentAllowedData,
            OffsetDateTime timestamp
    ) {
        List<ThresholdRuleResponse> rules =
                thresholdRuleService.getEnabledRulesByDevice(device.getId());

        if (rules == null || rules.isEmpty()) {
            return;
        }

        String currentDate = timestamp
                .atZoneSameInstant(DEFAULT_ZONE)
                .toLocalDate()
                .toString();

        for (ThresholdRuleResponse rule : rules) {
            if (!Boolean.TRUE.equals(rule.getIsEnabled())) {
                continue;
            }

            String metricType = rule.getMetricType();
            if (metricType == null || metricType.isBlank()) {
                continue;
            }

            Double currentValue =
                    resolveMetricValueByPath(currentAllowedData, metricType);

            if (currentValue == null) {
                continue;
            }

            String baseMetric = extractBaseMetric(metricType);
            boolean lowerIsWorse =
                    LOWER_IS_WORSE_METRICS.contains(baseMetric);

            String currentSeverity = resolveCurrentSeverity(
                    currentValue,
                    rule.getWarningLimit(),
                    rule.getCriticalLimit(),
                    lowerIsWorse
            );

            String streakKey =
                    STREAK_REDIS_PREFIX + rule.getId() + ":" + currentDate;

            if (currentSeverity == null) {
                redisTemplate.delete(streakKey);
                incidentService.resolveOpenIncident(
                        device.getId(),
                        metricType,
                        timestamp
                );
                continue;
            }

            Map<String, Object> sampleEntry = new LinkedHashMap<>();
            sampleEntry.put("value", currentValue);
            sampleEntry.put("severity", currentSeverity);
            sampleEntry.put("timestamp", timestamp.toString());

            String sampleJson;
            try {
                sampleJson = objectMapper.writeValueAsString(sampleEntry);
            } catch (JsonProcessingException e) {
                log.error(
                        "Failed to serialize breach sample for rule {}",
                        rule.getId(),
                        e
                );
                continue;
            }

            try {
                redisTemplate.opsForList().rightPush(streakKey, sampleJson);
                redisTemplate.expire(streakKey, STREAK_TTL);
            } catch (org.springframework.data.redis.RedisSystemException e) {
                if (e.getMessage() != null && e.getMessage().contains("WRONGTYPE")) {
                    log.warn("Resetting streak key due to WRONGTYPE: {}", streakKey);
                    redisTemplate.delete(streakKey);
                    redisTemplate.opsForList().rightPush(streakKey, sampleJson);
                    redisTemplate.expire(streakKey, STREAK_TTL);
                } else {
                    log.error(
                            "Redis error while recording breach sample for rule {}",
                            rule.getId(),
                            e
                    );
                    continue;
                }
            }

            Long streakSize =
                    redisTemplate.opsForList().size(streakKey);

            int requiredOccurrences =
                    rule.getConsecutiveOccurrences() != null
                            ? rule.getConsecutiveOccurrences()
                            : 3;

            if (streakSize == null || streakSize < requiredOccurrences) {
                continue;
            }

            List<String> rawSamples =
                    redisTemplate.opsForList().range(streakKey, 0, -1);

            ArrayNode parsedSamples =
                    parseBreachSamples(rawSamples);

            double peakValue =
                    calculatePeakValue(parsedSamples, lowerIsWorse);

            String highestSeverity =
                    resolveHighestSeverity(parsedSamples);

            double appliedLimit = "CRITICAL".equals(highestSeverity)
                    ? rule.getCriticalLimit()
                    : rule.getWarningLimit();

            incidentService.createOrEscalateIncident(
                    device,
                    rule,
                    highestSeverity,
                    appliedLimit,
                    peakValue,
                    parsedSamples.size(),
                    parsedSamples,
                    timestamp
            );
        }
    }

    private Double resolveMetricValueByPath(
            Map<String, Object> currentAllowedData,
            String metricType
    ) {
        if (currentAllowedData == null || metricType == null || metricType.isBlank()) {
            return null;
        }

        if (currentAllowedData.containsKey(metricType)) {
            return extractNumericValue(currentAllowedData.get(metricType));
        }

        String[] segments = metricType.split("-");
        if (segments.length < 2) {
            return null;
        }

        Object current = currentAllowedData.get(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }

            String targetSegment = segments[i];
            Object matched = map.get(targetSegment);

            if (matched == null) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null
                            && sanitizeSegment(String.valueOf(entry.getKey())).equals(targetSegment)) {
                        matched = entry.getValue();
                        break;
                    }
                }
            }

            if (matched == null) {
                return null;
            }
            current = matched;
        }

        return extractNumericValue(current);
    }

    private String extractBaseMetric(String metricType) {
        if (metricType == null) {
            return "";
        }
        int dashIndex = metricType.indexOf('-');
        return dashIndex >= 0 ? metricType.substring(0, dashIndex) : metricType;
    }

    private String sanitizeSegment(String segment) {
        if (segment == null) {
            return "";
        }
        return segment.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private ArrayNode parseBreachSamples(
            List<String> rawSamples
    ) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        if (rawSamples == null) {
            return arrayNode;
        }
        for (String json : rawSamples) {
            try {
                arrayNode.add(objectMapper.readTree(json));
            } catch (Exception ignored) {
            }
        }
        return arrayNode;
    }

    private double calculatePeakValue(
            ArrayNode samples,
            boolean lowerIsWorse
    ) {
        double peak = lowerIsWorse
                ? Double.POSITIVE_INFINITY
                : Double.NEGATIVE_INFINITY;

        for (JsonNode sample : samples) {
            JsonNode valNode = sample.get("value");
            if (valNode != null && valNode.isNumber()) {
                double v = valNode.asDouble();
                peak = lowerIsWorse
                        ? Math.min(peak, v)
                        : Math.max(peak, v);
            }
        }
        return peak;
    }

    private String resolveHighestSeverity(
            ArrayNode samples
    ) {
        for (JsonNode sample : samples) {
            JsonNode severityNode = sample.get("severity");
            if (severityNode != null && "CRITICAL".equals(severityNode.asText())) {
                return "CRITICAL";
            }
        }
        return "WARNING";
    }

    private Double extractNumericValue(Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }

        if (rawValue instanceof Map<?, ?> map) {
            double maxVal = Double.NEGATIVE_INFINITY;
            boolean hasNumber = false;

            for (Object val : map.values()) {
                if (val instanceof Number number) {
                    maxVal = Math.max(maxVal, number.doubleValue());
                    hasNumber = true;
                }
            }

            return hasNumber ? maxVal : null;
        }

        return null;
    }

    private String resolveCurrentSeverity(
            double currentValue,
            Double warningLimit,
            Double criticalLimit,
            boolean lowerIsWorse
    ) {
        if (criticalLimit != null
                && isBreached(currentValue, criticalLimit, lowerIsWorse)) {
            return "CRITICAL";
        }

        if (warningLimit != null
                && isBreached(currentValue, warningLimit, lowerIsWorse)) {
            return "WARNING";
        }

        return null;
    }

    private boolean isBreached(
            double currentValue,
            double limit,
            boolean lowerIsWorse
    ) {
        if (lowerIsWorse) {
            return currentValue <= limit;
        }
        return currentValue >= limit;
    }
}
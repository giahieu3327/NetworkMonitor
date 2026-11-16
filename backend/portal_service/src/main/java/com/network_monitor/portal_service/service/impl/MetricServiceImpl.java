
package com.network_monitor.portal_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.network_monitor.portal_service.model.dto.request.MetricRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.MetricResponse;

import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.Metric;

import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.MetricRepository;

import com.network_monitor.portal_service.service.MetricService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.network_monitor.portal_service.mapper.MetricHistoryMapper;
import com.network_monitor.portal_service.model.dto.response.MetricHistoryRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricServiceImpl implements MetricService {

    private final MetricRepository metricRepository;

    private final DeviceRepository deviceRepository;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final MetricHistoryMapper metricHistoryMapper;
    /*
     * =========================================================
     * REDIS CONFIGURATION
     * =========================================================
     */

    private static final String REDIS_PREFIX =
            "network-monitor:snmp:metric:";

    private static final int MAX_REDIS_SAMPLES = 15;

    private static final Duration REDIS_TTL =
            Duration.ofMinutes(20);

    /*
     * =========================================================
     * ALLOWED METRICS
     * =========================================================
     *
     * Chỉ lưu CPU / RAM / CORE.
     *
     * Không lưu:
     * - Interface In Octets
     * - Interface Out Octets
     * - Interface Throughput
     */

    private static final Set<String> ALLOWED_METRICS = Set.of(

            "cpu_usage",

            "core_usage",

            "core_count",

            "ram_usage_percent",

            "ram_usage",

            "ram_free",

            "ram_capacity"
    );

    /*
     * =========================================================
     * POSTGRESQL METRICS
     * =========================================================
     *
     * PostgreSQL lưu:
     * - CPU Usage
     * - RAM Usage Percentage
     *
     * cpu_usage có thể là:
     * - Number: Fortinet / Peplink
     * - Map: Cisco (CPU entity index -> percentage)
     */

    private static final Set<String> POSTGRES_METRICS = Set.of(

            "cpu_usage",

            "ram_usage_percent"
    );

    /*
     * =========================================================
     * INSERT METRIC
     * =========================================================
     */

    @Override
    @Transactional
    public ApiResponse<MetricResponse> insertMetric(
            MetricRequest request
    ) {

        try {

            if (request == null
                    || request.getDeviceId() == null) {

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

            Long deviceId =
                    request.getDeviceId();

            OffsetDateTime timestamp =
                    request.getTimestamp() != null
                            ? request.getTimestamp()
                            : OffsetDateTime.now();

            Device device =
                    deviceRepository.findById(deviceId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Device not found: " + deviceId
                                    )
                            );

            /*
             * 1. FILTER ALLOWED METRICS
             */

            Map<String, Object> allowedData =
                    filterAllowedMetrics(
                            request.getMetricData()
                    );

            if (allowedData.isEmpty()) {

                log.warn(
                        "No allowed metrics found. Device ID: {}",
                        deviceId
                );

                return ApiResponse.error(
                        "No supported metric data"
                );
            }

            /*
             * 2. SAVE ALL ALLOWED METRICS TO REDIS
             */

            saveAllMetricsToRedis(
                    deviceId,
                    allowedData,
                    timestamp
            );

            /*
             * 3. FILTER METRICS FOR POSTGRESQL
             */

            Map<String, Object> postgresData =
                    filterMetricsForPostgres(
                            allowedData
                    );

            /*
             * 4. SAVE POSTGRESQL
             */

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

            MetricResponse response =
                    MetricResponse.builder()
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

            log.error(
                    "Failed to insert metric",
                    e
            );

            return ApiResponse.error(
                    "Failed to insert metric: " + e.getMessage()
            );
        }
    }

    /*
     * =========================================================
     * FILTER ALLOWED METRICS
     * =========================================================
     */

    private Map<String, Object> filterAllowedMetrics(
            Map<String, Object> metricData
    ) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry :
                metricData.entrySet()) {

            String metricName =
                    normalizeMetricKey(entry.getKey());

            if (!ALLOWED_METRICS.contains(metricName)) {
                continue;
            }

            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            result.put(
                    metricName,
                    value
            );
        }

        return result;
    }

    /*
     * =========================================================
     * SAVE ALL METRICS TO REDIS
     * =========================================================
     *
     * Each metric has one Redis List.
     *
     * LPUSH:
     * Add newest sample to the beginning.
     *
     * LTRIM:
     * Keep maximum 15 samples.
     *
     * EXPIRE:
     * Expire after 20 minutes.
     */

    private void saveAllMetricsToRedis(
            Long deviceId,
            Map<String, Object> metricData,
            OffsetDateTime timestamp
    ) throws JsonProcessingException {

        for (Map.Entry<String, Object> entry :
                metricData.entrySet()) {

            String metricName =
                    normalizeMetricKey(entry.getKey());

            if (metricName.isBlank()) {
                continue;
            }

            String redisKey =
                    REDIS_PREFIX + deviceId + ":" + metricName;

            Map<String, Object> sample =
                    new LinkedHashMap<>();

            sample.put(
                    "deviceId",
                    deviceId
            );

            sample.put(
                    "metricName",
                    metricName
            );

            sample.put(
                    "value",
                    entry.getValue()
            );

            sample.put(
                    "timestamp",
                    timestamp.toString()
            );

            String json =
                    objectMapper.writeValueAsString(sample);

            /*
             * Insert newest sample.
             */

            redisTemplate.opsForList().leftPush(
                    redisKey,
                    json
            );

            /*
             * Keep latest 15 samples.
             */

            redisTemplate.opsForList().trim(
                    redisKey,
                    0,
                    MAX_REDIS_SAMPLES - 1
            );

            /*
             * Set TTL.
             */

            redisTemplate.expire(
                    redisKey,
                    REDIS_TTL
            );
        }
    }

    /*
     * =========================================================
     * NORMALIZE METRIC KEY
     * =========================================================
     */

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

    /*
     * =========================================================
     * FILTER METRICS FOR POSTGRESQL
     * =========================================================
     *
     * cpu_usage:
     * - Number
     * - Map
     *
     * ram_usage_percent:
     * - Number
     */

    private Map<String, Object> filterMetricsForPostgres(
            Map<String, Object> metricData
    ) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        for (String metricName : POSTGRES_METRICS) {

            Object value =
                    metricData.get(metricName);

            if (value == null) {
                continue;
            }

            if ("cpu_usage".equals(metricName)) {

                /*
                 * Cisco:
                 * {
                 *   "19": 1,
                 *   "20": 0
                 * }
                 *
                 * Fortinet / Peplink:
                 * 15.00
                 */

                if (value instanceof Number
                        || value instanceof Map<?, ?>) {

                    result.put(
                            metricName,
                            value
                    );
                }

            } else if (value instanceof Number) {

                result.put(
                        metricName,
                        value
                );
            }
        }

        return result;
    }

    /*
     * =========================================================
     * DELETE EXPIRED METRICS
     * =========================================================
     *
     * Delete PostgreSQL metrics older than 24 hours.
     *
     * Run at minute 00 every hour.
     */

    @Override
    @Transactional
    @Scheduled(
            cron = "0 0 * * * *",
            zone = "Asia/Ho_Chi_Minh"
    )
    public ApiResponse<Integer> deleteExpiredMetrics() {

        try {

            OffsetDateTime cutoff =
                    OffsetDateTime.now().minusHours(24);

            int deletedCount =
                    metricRepository.deleteExpiredMetrics(
                            cutoff
                    );

            log.info(
                    "Deleted {} expired metric records",
                    deletedCount
            );

            return ApiResponse.success(
                    "Expired metrics deleted successfully",
                    deletedCount
            );

        } catch (Exception e) {

            log.error(
                    "Failed to delete expired metrics",
                    e
            );

            return ApiResponse.error(
                    "Failed to delete expired metrics: "
                            + e.getMessage()
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

                Device device = deviceRepository.findById(deviceId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Device not found: " + deviceId
                                )
                        );

                Map<String, Object> result = new LinkedHashMap<>();

                for (String metricName : ALLOWED_METRICS) {

                String redisKey =
                        REDIS_PREFIX + deviceId + ":" + metricName;

                // LPUSH => index 0 là sample mới nhất
                String json = redisTemplate.opsForList()
                        .index(redisKey, 0);

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

                log.error(
                        "Failed to get latest metrics. Device ID: {}",
                        deviceId,
                        e
                );

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
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Device not found: " + deviceId
                                )
                        );

                List<MetricHistoryRow> rows =
                        metricHistoryMapper.findCpuHistory(
                                deviceId
                        );

                List<MetricResponse> result = new ArrayList<>();

                for (MetricHistoryRow row : rows) {

                if (row.getMetricValueJson() == null) {
                        continue;
                }

                Object cpuValue = objectMapper.readValue(
                        row.getMetricValueJson(),
                        Object.class
                );

                Map<String, Object> metricData =
                        new LinkedHashMap<>();

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

                // Đảo DESC thành ASC để hiển thị biểu đồ
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

                log.error(
                        "Failed to get CPU history. Device ID: {}",
                        deviceId,
                        e
                );

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
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Device not found: " + deviceId
                                )
                        );

                List<MetricHistoryRow> rows =
                        metricHistoryMapper.findRamHistory(
                                deviceId
                        );

                List<MetricResponse> result = new ArrayList<>();

                for (MetricHistoryRow row : rows) {

                if (row.getMetricValueJson() == null) {
                        continue;
                }

                Object ramValue = objectMapper.readValue(
                        row.getMetricValueJson(),
                        Object.class
                );

                Map<String, Object> metricData =
                        new LinkedHashMap<>();

                metricData.put(
                        "ram_usage_percent",
                        ramValue
                );

                MetricResponse response = MetricResponse.builder()
                        .id(row.getId())
                        .deviceId(deviceId)
                        .deviceName(device.getDeviceName())
                        .metricData(metricData)
                        .timestamp(row.getTimestamp())
                        .build();

                result.add(response);
                }

                // Đảo DESC thành ASC để hiển thị biểu đồ
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

                log.error(
                        "Failed to get RAM history. Device ID: {}",
                        deviceId,
                        e
                );

                return ApiResponse.error(
                        "Failed to get RAM history: " + e.getMessage()
                );
        }
        }
}

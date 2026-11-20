
package com.network_monitor.portal_service.service.snmp;

import com.network_monitor.portal_service.model.dto.metric.MetricConfig;
import com.network_monitor.portal_service.model.dto.request.MetricRequest;
import com.network_monitor.portal_service.model.dto.snmp.WalkResult;

import com.network_monitor.portal_service.service.MetricService;
import com.network_monitor.portal_service.service.SnmpConfigCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.time.OffsetDateTime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricScope {

    private final SnmpClient snmpClient;

    private final SnmpConfigCacheService configCacheService;

    private final MetricService metricService;

    /*
     * OID PREFIX
     */

    private static final String CISCO_PREFIX =
            "1.3.6.1.4.1.9.";

    private static final String FORTINET_PREFIX =
            "1.3.6.1.4.1.12356.";

    private static final String PEPLINK_PREFIX =
            "1.3.6.1.4.1.23695.";

    /*
     * =========================================================
     * POLL ALL DEVICES
     * =========================================================
     */

    public void pollAll() {

        List<MetricConfig> configs =
                configCacheService.getAllDeviceConfigs();

        if (configs == null || configs.isEmpty()) {

            log.warn("No device configurations found");

            return;
        }

        for (MetricConfig config : configs) {

            if (config == null || config.getDeviceId() == null) {
                continue;
            }

            try {

                poll(config.getDeviceId());

            } catch (Exception e) {

                log.error(
                        "Failed to poll metric for device {}",
                        config.getDeviceId(),
                        e
                );
            }
        }
    }

    /*
     * =========================================================
     * POLL DEVICE
     * =========================================================
     */

    public void poll(Long deviceId) {

        MetricConfig config =
                configCacheService.getDeviceConfig(deviceId);

        if (config == null) {

            log.warn(
                    "No configuration found for device {}",
                    deviceId
            );

            return;
        }

        collectDeviceMetrics(config);
    }

    /*
     * =========================================================
     * COLLECT DEVICE METRICS
     * =========================================================
     */

    private void collectDeviceMetrics(MetricConfig config) {

        Map<String, Object> rawMetrics =
                new LinkedHashMap<>();

        List<MetricConfig.Oid> oids =
                config.getOids();

        if (oids == null || oids.isEmpty()) {

            log.warn(
                    "No OID configuration for device {}",
                    config.getDeviceId()
            );

            return;
        }

        /*
         * 1. SNMP GET / WALK
         */

        for (MetricConfig.Oid oid : oids) {

            if (oid == null) {
                continue;
            }

            if (!"METRIC".equalsIgnoreCase(
                    oid.getMetricScope()
            )) {
                continue;
            }

            String metricType = oid.getMetricType();

            String oidPattern = oid.getOidPattern();

            String oidKind = oid.getOidKind();

            if (metricType == null || oidPattern == null) {
                continue;
            }

            try {

                Object value;

                if ("SINGLE".equalsIgnoreCase(oidKind)) {

                    value = collectSingle(config, oid);

                } else if ("TABLE_COLUMN".equalsIgnoreCase(oidKind)) {

                    value = collectTable(config, oid);

                } else {

                    log.warn(
                            "Unsupported OID kind: {}",
                            oidKind
                    );

                    continue;
                }

                if (value != null) {

                    rawMetrics.put(metricType, value);
                }

            } catch (Exception e) {

                log.error(
                        "Failed to collect {} from device {}",
                        metricType,
                        config.getDeviceId(),
                        e
                );
            }
        }

        if (rawMetrics.isEmpty()) {

            log.warn(
                    "No metric data collected for device {}",
                    config.getDeviceId()
            );

            return;
        }

        /*
         * 2. NORMALIZE METRICS
         */

        Map<String, Object> normalizedMetrics =
                normalizeMetrics(config, rawMetrics);

        if (normalizedMetrics.isEmpty()) {

            log.warn(
                    "No normalized metric data for device {}",
                    config.getDeviceId()
            );

            return;
        }

        /*
         * 3. SAVE REDIS + POSTGRESQL
         */

        OffsetDateTime timestamp =
                OffsetDateTime.now();

        MetricRequest request = new MetricRequest();

        request.setDeviceId(config.getDeviceId());

        request.setMetricData(normalizedMetrics);

        request.setTimestamp(timestamp);

        metricService.insertMetric(request);

        log.info(
                "Metric polling completed. Device: {}, data: {}",
                config.getDeviceId(),
                normalizedMetrics
        );
    }

    /*
     * =========================================================
     * SNMP GET
     * =========================================================
     */

    private Object collectSingle(
            MetricConfig config,
            MetricConfig.Oid oid
    ) throws Exception {

        String value = snmpClient.get(
                config.getIpAddress(),
                config.getCredentials(),
                oid.getOidPattern()
        );

        if (value == null || value.isBlank()) {
            return null;
        }

        return convertValue(
                value,
                oid.getDataType(),
                oid.getMultiplier()
        );
    }

    /*
     * =========================================================
     * SNMP WALK
     * =========================================================
     */

    private Object collectTable(
            MetricConfig config,
            MetricConfig.Oid oid
    ) throws Exception {

        WalkResult walkResult = snmpClient.walk(
                config.getIpAddress(),
                config.getCredentials(),
                oid.getOidPattern()
        );

        if (walkResult == null
                || !walkResult.success()
                || walkResult.values() == null) {

            return null;
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry :
                walkResult.values().entrySet()) {

            String fullOid =
                    normalizeOid(entry.getKey());

            String suffix = getOidSuffix(
                    oid.getOidPattern(),
                    fullOid
            );

            if (suffix == null || suffix.isBlank()) {
                continue;
            }

            Object value = convertValue(
                    String.valueOf(entry.getValue()),
                    oid.getDataType(),
                    oid.getMultiplier()
            );

            if (value != null) {

                result.put(suffix, value);
            }
        }

        return result.isEmpty() ? null : result;
    }

    /*
     * =========================================================
     * NORMALIZE METRICS
     * =========================================================
     */

    private Map<String, Object> normalizeMetrics(
            MetricConfig config,
            Map<String, Object> raw
    ) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        boolean isCisco = isCisco(config);

        boolean isFortinet = isFortinet(config);

        boolean isPeplink = isPeplink(config);

        /*
         * -----------------------------------------------------
         * 1. CPU USAGE
         * -----------------------------------------------------
         */

        Object cpuRaw =
                findMetric(raw, "CPU Usage");

        if (cpuRaw instanceof Map<?, ?> cpuMap) {

            if (isCisco) {

                /*
                 * CISCO
                 *
                 * OID:
                 * cpmCPUTotal1minRev
                 *
                 * .7.19 = 1
                 * .7.20 = 0
                 *
                 * Không lấy trung bình.
                 * Không chia 100.
                 * Giữ nguyên từng CPU entity.
                 */

                Map<String, Object> cpuUsage =
                        new LinkedHashMap<>();

                for (Map.Entry<?, ?> entry :
                        cpuMap.entrySet()) {

                    String cpuIndex =
                            String.valueOf(entry.getKey());

                    BigDecimal value =
                            toBigDecimal(entry.getValue());

                    if (value == null) {
                        continue;
                    }

                    cpuUsage.put(
                            cpuIndex,
                            percentage(value)
                    );
                }

                if (!cpuUsage.isEmpty()) {

                    result.put(
                            "cpu_usage",
                            cpuUsage
                    );
                }

            } else {

                /*
                 * FORTINET / PEPLINK
                 *
                 * Nếu CPU Usage được thu thập bằng TABLE_COLUMN,
                 * lấy trung bình các giá trị.
                 */

                BigDecimal average =
                        averageMap(cpuMap);

                putIfNotNull(
                        result,
                        "cpu_usage",
                        percentage(average)
                );
            }

        } else {

            /*
             * SINGLE OID:
             * Fortinet / Peplink
             */

            BigDecimal cpuUsage =
                    toBigDecimal(cpuRaw);

            putIfNotNull(
                    result,
                    "cpu_usage",
                    percentage(cpuUsage)
            );
        }

        /*
         * -----------------------------------------------------
         * 2. CORE USAGE
         * -----------------------------------------------------
         */

        Object coreRaw =
                findMetric(raw, "Core Usage");

        if (coreRaw instanceof Map<?, ?> coreMap) {

            if (isCisco) {

                /*
                 * CISCO CORE STRUCTURE
                 *
                 * WALK suffix:
                 *
                 * 19.0 = 1
                 * 19.1 = 1
                 * 20.0 = 1
                 * 20.1 = 1
                 *
                 * Structure:
                 *
                 * CPU index -> Core index -> Usage
                 */

                Map<String, Object> cpuCoreUsage =
                        new LinkedHashMap<>();

                int totalCoreCount = 0;

                for (Map.Entry<?, ?> entry :
                        coreMap.entrySet()) {

                    String suffix =
                            String.valueOf(entry.getKey());

                    String[] indexes =
                            suffix.split("\\.");

                    if (indexes.length != 2) {

                        log.warn(
                                "Invalid Cisco core OID suffix: {}",
                                suffix
                        );

                        continue;
                    }

                    String cpuIndex = indexes[0];

                    String coreIndex = indexes[1];

                    BigDecimal value =
                            toBigDecimal(entry.getValue());

                    if (value == null) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> coreMapByCpu =
                            (Map<String, Object>) cpuCoreUsage
                                    .computeIfAbsent(
                                            cpuIndex,
                                            key -> new LinkedHashMap<String, Object>()
                                    );

                    coreMapByCpu.put(
                            coreIndex,
                            percentage(value)
                    );

                    totalCoreCount++;
                }

                if (!cpuCoreUsage.isEmpty()) {

                    result.put(
                            "core_usage",
                            cpuCoreUsage
                    );

                    /*
                     * Cisco hiện tại:
                     * CPU 19: 8 cores
                     * CPU 20: 8 cores
                     *
                     * Total = 16 cores
                     */

                    BigDecimal configuredCoreCount =
                            toBigDecimal(
                                    findMetric(raw, "Core Count")
                            );

                    BigDecimal coreCount =
                            configuredCoreCount != null
                                    ? configuredCoreCount
                                    : BigDecimal.valueOf(totalCoreCount);

                    putIfNotNull(
                            result,
                            "core_count",
                            coreCount
                    );
                }

            } else {

                /*
                 * FORTINET / PEPLINK
                 *
                 * Giữ cấu trúc TABLE_COLUMN thông thường.
                 */

                Map<String, Object> coreUsage =
                        new LinkedHashMap<>();

                for (Map.Entry<?, ?> entry :
                        coreMap.entrySet()) {

                    BigDecimal value =
                            toBigDecimal(entry.getValue());

                    if (value != null) {

                        coreUsage.put(
                                String.valueOf(entry.getKey()),
                                percentage(value)
                        );
                    }
                }

                if (!coreUsage.isEmpty()) {

                    result.put(
                            "core_usage",
                            coreUsage
                    );

                    BigDecimal coreCount =
                            toBigDecimal(
                                    findMetric(raw, "Core Count")
                            );

                    if (coreCount == null && isFortinet) {

                        coreCount = BigDecimal.valueOf(
                                coreUsage.size()
                        );
                    }

                    putIfNotNull(
                            result,
                            "core_count",
                            coreCount
                    );
                }
            }

        } else {

            putIfNotNull(
                    result,
                    "core_count",
                    toBigDecimal(
                            findMetric(raw, "Core Count")
                    )
            );
        }

        /*
         * -----------------------------------------------------
         * 3. RAM
         * -----------------------------------------------------
         */

        BigDecimal ramUsage = null;

        BigDecimal ramFree = null;

        BigDecimal ramCapacity = null;

        BigDecimal ramUsagePercent = null;

        if (isCisco) {

            /*
             * CISCO
             *
             * Mem Used = TABLE_COLUMN
             * Mem Free = TABLE_COLUMN
             *
             * Sum all memory pools.
             */

            BigDecimal used =
                    sumMap(findMetric(raw, "Mem Used"));

            BigDecimal free =
                    sumMap(findMetric(raw, "Mem Free"));

            if (used != null && free != null) {

                ramUsage = used;

                ramFree = free;

                ramCapacity = used.add(free);

                ramUsagePercent = calculatePercentage(
                        ramUsage,
                        ramCapacity
                );
            }

        } else if (isFortinet) {

            /*
             * FORTINET
             *
             * Mem Usage = percentage
             * Mem Capacity = total RAM
             */

            BigDecimal usagePercent =
                    toBigDecimal(
                            findMetric(raw, "Mem Usage")
                    );

            ramCapacity =
                    toBigDecimal(
                            findMetric(raw, "Mem Capacity")
                    );

            if (usagePercent != null && ramCapacity != null) {

                ramUsagePercent = usagePercent;

                ramUsage = ramCapacity
                        .multiply(usagePercent)
                        .divide(
                                BigDecimal.valueOf(100),
                                4,
                                RoundingMode.HALF_UP
                        );

                ramFree = ramCapacity.subtract(ramUsage);
            }

        } else if (isPeplink) {

            /*
             * PEPLINK
             *
             * Mem Usage = used RAM
             * Mem Total = total RAM
             */

            ramUsage =
                    toBigDecimal(
                            findMetric(raw, "Mem Usage")
                    );

            ramCapacity =
                    toBigDecimal(
                            findMetric(raw, "Mem Total")
                    );

            if (ramUsage != null && ramCapacity != null) {

                ramFree = ramCapacity.subtract(ramUsage);

                ramUsagePercent = calculatePercentage(
                        ramUsage,
                        ramCapacity
                );
            }
        }

        putIfNotNull(
                result,
                "ram_usage_percent",
                percentage(ramUsagePercent)
        );

        putIfNotNull(
                result,
                "ram_usage",
                ramUsage
        );

        putIfNotNull(
                result,
                "ram_free",
                ramFree
        );

        putIfNotNull(
                result,
                "ram_capacity",
                ramCapacity
        );

        return result;
    }

    /*
     * =========================================================
     * HELPER: FIND METRIC
     * =========================================================
     */

    private Object findMetric(
            Map<String, Object> raw,
            String metricName
    ) {

        for (Map.Entry<String, Object> entry :
                raw.entrySet()) {

            if (entry.getKey() != null
                    && entry.getKey().equalsIgnoreCase(metricName)) {

                return entry.getValue();
            }
        }

        return null;
    }

    /*
     * =========================================================
     * HELPER: AVERAGE MAP
     * =========================================================
     */

    private BigDecimal averageMap(Map<?, ?> values) {

        BigDecimal sum = BigDecimal.ZERO;

        int count = 0;

        for (Object value : values.values()) {

            BigDecimal number =
                    toBigDecimal(value);

            if (number != null) {

                sum = sum.add(number);

                count++;
            }
        }

        if (count == 0) {
            return null;
        }

        return sum.divide(
                BigDecimal.valueOf(count),
                4,
                RoundingMode.HALF_UP
        );
    }

    /*
     * =========================================================
     * HELPER: SUM MAP
     * =========================================================
     */

    private BigDecimal sumMap(Object values) {

        if (!(values instanceof Map<?, ?> map)) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;

        int count = 0;

        for (Object value : map.values()) {

            BigDecimal number =
                    toBigDecimal(value);

            if (number != null) {

                sum = sum.add(number);

                count++;
            }
        }

        return count == 0 ? null : sum;
    }

    /*
     * =========================================================
     * HELPER: CALCULATE PERCENTAGE
     * =========================================================
     */

    private BigDecimal calculatePercentage(
            BigDecimal used,
            BigDecimal capacity
    ) {

        if (used == null
                || capacity == null
                || capacity.compareTo(BigDecimal.ZERO) <= 0) {

            return null;
        }

        return used
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        capacity,
                        4,
                        RoundingMode.HALF_UP
                );
    }

    /*
     * =========================================================
     * HELPER: FORMAT PERCENTAGE
     * =========================================================
     */

    private BigDecimal percentage(BigDecimal value) {

        if (value == null) {
            return null;
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    /*
     * =========================================================
     * HELPER: CONVERT VALUE TO BIGDECIMAL
     * =========================================================
     */

    private BigDecimal toBigDecimal(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal decimal) {
            return decimal;
        }

        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }

        String text = value.toString().trim();

        if (text.isBlank()) {
            return null;
        }

        /*
         * Gauge32: 27
         * Counter64: 2401680
         */

        if (text.contains(":")) {

            text = text.substring(
                    text.lastIndexOf(':') + 1
            ).trim();
        }

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile("-?\\d+(\\.\\d+)?")
                        .matcher(text);

        if (!matcher.find()) {
            return null;
        }

        try {

            return new BigDecimal(
                    matcher.group()
            );

        } catch (NumberFormatException e) {

            return null;
        }
    }

    /*
     * =========================================================
     * HELPER: CONVERT SNMP VALUE
     * =========================================================
     */

    private Object convertValue(
            String rawValue,
            String dataType,
            Number multiplier
    ) {

        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String type = dataType == null
                ? ""
                : dataType.toUpperCase();

        if ("STRING".equals(type)
                || "OCTET_STRING".equals(type)) {

            return rawValue;
        }

        BigDecimal value =
                toBigDecimal(rawValue);

        if (value == null) {
            return rawValue;
        }

        /*
         * TIMETICKS:
         * 1 tick = 1/100 second.
         */

        if ("TIMETICKS".equals(type)) {

            value = value.divide(
                    BigDecimal.valueOf(100),
                    4,
                    RoundingMode.HALF_UP
            );
        }

        if (multiplier != null) {

            value = value.multiply(
                    new BigDecimal(
                            multiplier.toString()
                    )
            );
        }

        return value;
    }

    /*
     * =========================================================
     * HELPER: NORMALIZE OID
     * =========================================================
     */

    private String normalizeOid(String oid) {

        if (oid == null) {
            return "";
        }

        String result = oid.trim();

        /*
         * iso.3.6... -> 1.3.6...
         */

        if (result.startsWith("iso.")) {

            result = "1." + result.substring(4);
        }

        while (result.startsWith(".")) {

            result = result.substring(1);
        }

        return result;
    }

    /*
     * =========================================================
     * HELPER: GET OID SUFFIX
     * =========================================================
     */

    private String getOidSuffix(
            String baseOid,
            String fullOid
    ) {

        String base =
                normalizeOid(baseOid);

        String full =
                normalizeOid(fullOid);

        String prefix = base + ".";

        if (!full.startsWith(prefix)) {
            return null;
        }

        return full.substring(
                prefix.length()
        );
    }

    /*
     * =========================================================
     * HELPER: DEVICE VENDOR
     * =========================================================
     */

    private boolean hasVendorOid(
            MetricConfig config,
            String prefix
    ) {

        if (config == null || config.getOids() == null) {
            return false;
        }

        return config.getOids()
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(oid ->
                        normalizeOid(oid.getOidPattern())
                                .startsWith(prefix)
                );
    }

    private boolean isCisco(MetricConfig config) {

        return hasVendorOid(
                config,
                CISCO_PREFIX
        );
    }

    private boolean isFortinet(MetricConfig config) {

        return hasVendorOid(
                config,
                FORTINET_PREFIX
        );
    }

    private boolean isPeplink(MetricConfig config) {

        return hasVendorOid(
                config,
                PEPLINK_PREFIX
        );
    }

    /*
     * =========================================================
     * HELPER: PUT IF NOT NULL
     * =========================================================
     */

    private void putIfNotNull(
            Map<String, Object> map,
            String key,
            Object value
    ) {

        if (value != null) {

            map.put(key, value);
        }
    }
}

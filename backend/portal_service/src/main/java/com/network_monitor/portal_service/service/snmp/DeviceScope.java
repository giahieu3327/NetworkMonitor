package com.network_monitor.portal_service.service.snmp;

import com.network_monitor.portal_service.model.dto.metric.MetricConfig;
import com.network_monitor.portal_service.service.SnmpConfigCacheService;
import com.network_monitor.portal_service.model.dto.snmp.WalkResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceScope {

    private static final String SCOPE_DEVICE = "DEVICE";
    private static final String OID_KIND_SINGLE = "SINGLE";

    private final SnmpClient snmpClient;
    private final SnmpConfigCacheService configCacheService;

    public Map<String, Object> poll(Long deviceId) {

        MetricConfig config =
                configCacheService.getDeviceConfig(deviceId);

        List<MetricConfig.Oid> oids =
                getOidsByScope(config);

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("deviceId", deviceId);
        result.put("deviceName", config.getDeviceName());
        result.put("ipAddress", config.getIpAddress());
        result.put("deviceType", config.getDeviceType());

        Map<String, Object> deviceData =
                new LinkedHashMap<>();

        for (MetricConfig.Oid oidConfig : oids) {

            String oid = oidConfig.getOidPattern();
            String metricType = oidConfig.getMetricType();

            try {

                String oidKind = getOidKind(oidConfig);

                if (OID_KIND_SINGLE.equals(oidKind)) {

                    String value = snmpClient.get(
                            config.getIpAddress(),
                            config.getCredentials(),
                            oid
                    );

                    if (value != null) {
                        deviceData.put(
                                metricType,
                                convertValue(value, oidConfig)
                        );
                    }

                } else {

                    WalkResult walkResult = snmpClient.walk(
                            config.getIpAddress(),
                            config.getCredentials(),
                            oid
                    );

                    if (!walkResult.success()) {
                        log.warn(
                                "DEVICE WALK failed: device={}, metric={}, oid={}",
                                deviceId,
                                metricType,
                                oid
                        );
                        continue;
                    }

                    Map<String, Object> tableData =
                            new LinkedHashMap<>();

                    for (Map.Entry<String, Object> entry :
                            walkResult.values().entrySet()) {

                        String suffix = getOidSuffix(
                                oid,
                                entry.getKey()
                        );

                        tableData.put(
                                suffix != null ? suffix : entry.getKey(),
                                convertValue(entry.getValue(), oidConfig)
                        );
                    }

                    if (!tableData.isEmpty()) {
                        deviceData.put(metricType, tableData);
                    }
                }

            } catch (Exception e) {

                log.warn(
                        "DEVICE polling failed: device={}, metric={}, oid={}",
                        deviceId,
                        metricType,
                        oid,
                        e
                );
            }
        }

        result.put("device", deviceData);

        return result;
    }

    private List<MetricConfig.Oid> getOidsByScope(
            MetricConfig config) {

        if (config == null || config.getOids() == null) {
            return new ArrayList<>();
        }

        return config.getOids()
                .stream()
                .filter(Objects::nonNull)
                .filter(oid -> SCOPE_DEVICE.equalsIgnoreCase(
                        oid.getMetricScope()
                ))
                .toList();
    }

    private String getOidKind(MetricConfig.Oid oidConfig) {

        if (oidConfig.getOidKind() == null) {
            throw new IllegalArgumentException(
                    "OID kind must not be null"
            );
        }

        String oidKind = oidConfig.getOidKind()
                .trim()
                .toUpperCase();

        return switch (oidKind) {
            case "SINGLE", "TABLE_COLUMN" -> oidKind;
            default -> throw new IllegalArgumentException(
                    "Unsupported OID kind: " + oidKind
            );
        };
    }

    private Object convertValue(
            Object rawValue,
            MetricConfig.Oid oidConfig) {

        if (rawValue == null) {
            return null;
        }

        String value = rawValue.toString();
        String dataType = oidConfig.getDataType();

        double multiplier = oidConfig.getMultiplier() != null
                ? oidConfig.getMultiplier()
                : 1.0;

        if (dataType == null) {
            return value;
        }

        try {

            return switch (dataType.toUpperCase()) {

                case "INTEGER", "INTEGER32",
                    "GAUGE", "GAUGE32",
                    "COUNTER", "COUNTER32",
                    "COUNTER64", "UNSIGNED32" -> {

                    double number =
                            Double.parseDouble(value) * multiplier;

                    yield number % 1 == 0
                            ? (Object) (long) number
                            : number;
                }

                case "TIMETICKS" -> {

                    double seconds =
                            (Double.parseDouble(value) / 100.0)
                                    * multiplier;

                    yield seconds % 1 == 0
                            ? (Object) (long) seconds
                            : seconds;
                }

                default -> value;
            };

        } catch (NumberFormatException e) {
            return value;
        }
    }

    private String getOidSuffix(
            String baseOid,
            String fullOid) {

        if (baseOid == null || fullOid == null) {
            return null;
        }

        String prefix = baseOid + ".";

        if (!fullOid.startsWith(prefix)) {
            return null;
        }

        return fullOid.substring(prefix.length());
    }
}

package com.network_monitor.portal_service.service.snmp;

import com.network_monitor.portal_service.model.dto.metric.MetricConfig;
import com.network_monitor.portal_service.model.dto.snmp.WalkResult;
import com.network_monitor.portal_service.service.SnmpConfigCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterfaceScope {

    private static final String SCOPE_INTERFACE = "INTERFACE";

    private final SnmpClient snmpClient;
    private final SnmpConfigCacheService configCacheService;

    public Map<String, Object> poll(Long deviceId) {

        MetricConfig config =
                configCacheService.getDeviceConfig(deviceId);

        List<MetricConfig.Oid> oids = config.getOids()
                .stream()
                .filter(oid -> oid != null)
                .filter(oid -> SCOPE_INTERFACE.equalsIgnoreCase(
                        oid.getMetricScope()
                ))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("deviceId", deviceId);
        result.put("deviceName", config.getDeviceName());

        // LAN information: Peplink
        result.put("lanIpAddress", null);
        result.put("lanSubnetMask", null);

        Map<String, Map<String, Object>> interfaces =
                new LinkedHashMap<>();

        Map<String, Map<String, Object>> tableValues =
                new LinkedHashMap<>();

        boolean interfaceNameWalkSuccess = false;
        boolean allWalksSuccessful = true;

        // =============================================
        // 1. POLL SINGLE VÀ TABLE_COLUMN
        // =============================================

        for (MetricConfig.Oid oidConfig : oids) {

            String oid = oidConfig.getOidPattern();
            String metricType = oidConfig.getMetricType();

            try {

                String oidKind = getOidKind(oidConfig);

                // -----------------------------------------
                // SINGLE -> GET
                // -----------------------------------------

                if ("SINGLE".equals(oidKind)) {

                    Integer interfaceIndex =
                            oidConfig.getInterfaceIndex();

                    String instanceOid = oid;

                    if (interfaceIndex != null) {
                        instanceOid = buildInterfaceInstanceOid(
                                oid,
                                interfaceIndex
                        );
                    }

                    String value = snmpClient.get(
                            config.getIpAddress(),
                            config.getCredentials(),
                            instanceOid
                    );

                    if (value == null) {
                        continue;
                    }

                    Object convertedValue =
                            convertValue(value, oidConfig);

                    // -------------------------------------
                    // PEPLINK LAN INFORMATION
                    // -------------------------------------

                    if ("Interface LAN Ip Address"
                            .equalsIgnoreCase(metricType)) {

                        result.put(
                                "lanIpAddress",
                                convertedValue
                        );

                    } else if ("Interface LAN Subnet Mask"
                            .equalsIgnoreCase(metricType)) {

                        result.put(
                                "lanSubnetMask",
                                convertedValue
                        );

                    } else if (interfaceIndex != null) {

                        interfaces.computeIfAbsent(
                                String.valueOf(interfaceIndex),
                                key -> new LinkedHashMap<>()
                        ).put(
                                metricType,
                                convertedValue
                        );

                    } else {

                        // Các SINGLE khác nếu có
                        result.put(
                                metricType,
                                convertedValue
                        );
                    }

                    continue;
                }

                // -----------------------------------------
                // TABLE_COLUMN -> WALK
                // -----------------------------------------

                WalkResult walkResult = snmpClient.walk(
                        config.getIpAddress(),
                        config.getCredentials(),
                        oid
                );

                if (!walkResult.success()) {

                    allWalksSuccessful = false;

                    log.warn(
                            "Interface WALK failed: device={}, metric={}, oid={}",
                            deviceId,
                            metricType,
                            oid
                    );

                    continue;
                }

                Map<String, Object> values =
                        walkResult.values();

                if ("Interface Name".equalsIgnoreCase(metricType)) {
                    interfaceNameWalkSuccess = !values.isEmpty();
                }

                Map<String, Object> metricValues =
                        tableValues.computeIfAbsent(
                                metricType,
                                key -> new LinkedHashMap<>()
                        );

                for (Map.Entry<String, Object> entry :
                        values.entrySet()) {

                    String suffix = getOidSuffix(
                            oid,
                            entry.getKey()
                    );

                    if (suffix == null || suffix.isBlank()) {
                        continue;
                    }

                    if (oidConfig.getInterfaceIndex() != null
                            && !suffix.equals(
                                    String.valueOf(
                                            oidConfig.getInterfaceIndex()
                                    ))) {
                        continue;
                    }

                    metricValues.put(
                            suffix,
                            convertValue(entry.getValue(), oidConfig)
                    );
                }

            } catch (Exception e) {

                if ("TABLE_COLUMN".equalsIgnoreCase(
                        oidConfig.getOidKind())) {
                    allWalksSuccessful = false;
                }

                log.warn(
                        "INTERFACE polling failed: device={}, metric={}, oid={}",
                        deviceId,
                        metricType,
                        oid,
                        e
                );
            }
        }

        // =============================================
        // 2. GHÉP INTERFACE THEO ifIndex
        // =============================================

        Set<String> specialMetrics = Set.of(
                "Interface Ip-Index",
                "Interface Ip Address",
                "Interface Subnet Mask",
                "Interface WAN Name",
                "Interface WAN Ip Address",
                "Interface WAN Subnet Mask"
        );

        for (Map.Entry<String, Map<String, Object>> metricEntry :
                tableValues.entrySet()) {

            String metricType = metricEntry.getKey();

            if (specialMetrics.contains(metricType)) {
                continue;
            }

            for (Map.Entry<String, Object> valueEntry :
                    metricEntry.getValue().entrySet()) {

                String index = valueEntry.getKey();

                if (!isNumericIndex(index)) {
                    continue;
                }

                interfaces.computeIfAbsent(
                        index,
                        key -> new LinkedHashMap<>()
                ).put(
                        metricType,
                        valueEntry.getValue()
                );
            }
        }

        // =============================================
        // 3. GHÉP IP-MIB CHO CISCO / FORTINET
        // =============================================

        Map<String, Object> ipIndexes =
                tableValues.getOrDefault(
                        "Interface Ip-Index",
                        Collections.emptyMap()
                );

        Map<String, Object> ipAddresses =
                tableValues.getOrDefault(
                        "Interface Ip Address",
                        Collections.emptyMap()
                );

        Map<String, Object> subnetMasks =
                tableValues.getOrDefault(
                        "Interface Subnet Mask",
                        Collections.emptyMap()
                );

        for (Map.Entry<String, Object> ipEntry :
                ipAddresses.entrySet()) {

            String ipSuffix = ipEntry.getKey();

            Object interfaceIndexValue = ipIndexes.get(ipSuffix);

            if (interfaceIndexValue == null) {
                continue;
            }

            String interfaceIndex =
                    String.valueOf(interfaceIndexValue);

            if (!isNumericIndex(interfaceIndex)) {
                continue;
            }

            Map<String, Object> interfaceData =
                    interfaces.computeIfAbsent(
                            interfaceIndex,
                            key -> new LinkedHashMap<>()
                    );

            interfaceData.put(
                    "Interface Ip Address",
                    ipEntry.getValue()
            );

            Object subnetMask = subnetMasks.get(ipSuffix);

            if (subnetMask != null) {
                interfaceData.put(
                        "Interface Subnet Mask",
                        subnetMask
                );
            }
        }

        // =============================================
        // 4. GHÉP WAN CHO PEPLINK
        // =============================================

        Map<String, Object> wanNames =
                tableValues.getOrDefault(
                        "Interface WAN Name",
                        Collections.emptyMap()
                );

        Map<String, Object> wanAddresses =
                tableValues.getOrDefault(
                        "Interface WAN Ip Address",
                        Collections.emptyMap()
                );

        Map<String, Object> wanSubnetMasks =
                tableValues.getOrDefault(
                        "Interface WAN Subnet Mask",
                        Collections.emptyMap()
                );

        // Map Interface Name -> ifIndex
        Map<String, String> interfaceNameToIndex =
                new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry :
                interfaces.entrySet()) {

            Object interfaceName =
                    entry.getValue().get("Interface Name");

            if (interfaceName != null) {
                interfaceNameToIndex.put(
                        String.valueOf(interfaceName).trim(),
                        entry.getKey()
                );
            }
        }

        // Ghép WAN IP và subnet mask vào interface tương ứng
        for (Map.Entry<String, Object> wanEntry :
                wanNames.entrySet()) {

            String wanName = String.valueOf(
                    wanEntry.getValue()
            ).trim();

            if (wanName.isBlank()) {
                continue;
            }

            String interfaceIndex =
                    interfaceNameToIndex.get(wanName);

            if (interfaceIndex == null) {
                log.debug(
                        "Peplink WAN has no matching interface: device={}, wanName={}",
                        deviceId,
                        wanName
                );
                continue;
            }

            Map<String, Object> interfaceData =
                    interfaces.get(interfaceIndex);

            // Không đưa Interface WAN Name vào JSON.
            // Chỉ dùng wanName để tìm đúng interface.

            String wanSlot = normalizeWanSlot(
                    wanEntry.getKey()
            );

            Object wanIp = findWanValue(
                    wanAddresses,
                    wanSlot
            );

            Object wanMask = findWanValue(
                    wanSubnetMasks,
                    wanSlot
            );

            if (isValidIp(wanIp)) {
                interfaceData.put(
                        "Interface Ip Address",
                        wanIp
                );
            }

            if (isValidIp(wanMask)) {
                interfaceData.put(
                        "Interface Subnet Mask",
                        wanMask
                );
            }
        }

        // =============================================
        // 5. KẾT QUẢ
        // =============================================

        result.put("interfaces", interfaces);

        boolean discoverySuccess =
                interfaceNameWalkSuccess && allWalksSuccessful;

        result.put("discoverySuccess", discoverySuccess);

        log.info(
                "INTERFACE discovery completed: device={}, interfaces={}, success={}",
                deviceId,
                interfaces.size(),
                discoverySuccess
        );

        return result;
    }

    private String getOidKind(MetricConfig.Oid oidConfig) {

        if (oidConfig.getOidKind() == null) {
            throw new IllegalArgumentException(
                    "OID kind must not be null"
            );
        }

        String kind = oidConfig.getOidKind()
                .trim()
                .toUpperCase();

        return switch (kind) {
            case "SINGLE", "TABLE_COLUMN" -> kind;
            default -> throw new IllegalArgumentException(
                    "Unsupported OID kind: " + kind
            );
        };
    }

    private String buildInterfaceInstanceOid(
            String baseOid,
            Integer interfaceIndex) {

        if (baseOid == null || baseOid.isBlank()) {
            throw new IllegalArgumentException(
                    "OID pattern must not be blank"
            );
        }

        if (interfaceIndex == null || interfaceIndex <= 0) {
            throw new IllegalArgumentException(
                    "Valid interface index is required"
            );
        }

        String suffix = "." + interfaceIndex;

        return baseOid.endsWith(suffix)
                ? baseOid
                : baseOid + suffix;
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

    private boolean isNumericIndex(String value) {

        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String normalizeWanSlot(String suffix) {

        if (suffix == null || suffix.isBlank()) {
            return null;
        }

        return suffix.split("\\.")[0];
    }

    private Object findWanValue(
            Map<String, Object> values,
            String wanSlot) {

        if (wanSlot == null) {
            return null;
        }

        for (Map.Entry<String, Object> entry :
                values.entrySet()) {

            String slot = normalizeWanSlot(entry.getKey());

            if (wanSlot.equals(slot)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private boolean isValidIp(Object value) {

        if (value == null) {
            return false;
        }

        String ip = String.valueOf(value).trim();

        return !ip.isBlank() && !"0.0.0.0".equals(ip);
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
}

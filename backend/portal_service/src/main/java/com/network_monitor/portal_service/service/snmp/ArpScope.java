
package com.network_monitor.portal_service.service.snmp;

import com.network_monitor.portal_service.model.dto.metric.MetricConfig;
import com.network_monitor.portal_service.model.dto.snmp.WalkResult;
import com.network_monitor.portal_service.service.SnmpConfigCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArpScope {

    private static final String SCOPE_ARP = "ARP";

    private final SnmpClient snmpClient;
    private final SnmpConfigCacheService configCacheService;

    public Map<String, Object> poll(Long deviceId) {
        
        MetricConfig config =
                configCacheService.getDeviceConfig(deviceId);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("deviceId", deviceId);
        result.put("deviceName", config.getDeviceName());

        // Key nội bộ: interfaceIndex + IP
        // Output chỉ chứa IP và MAC.
        Map<String, Map<String, Object>> arpEntries =
                new LinkedHashMap<>();

        List<MetricConfig.Oid> oids = config.getOids()
                .stream()
                .filter(oid -> oid != null)
                .filter(oid -> SCOPE_ARP.equalsIgnoreCase(
                        oid.getMetricScope()
                ))
                .toList();

        log.info(
                "ARP polling started: device={}, oidCount={}",
                deviceId, oids.size()
        );

        boolean discoverySuccess = !oids.isEmpty();

        for (MetricConfig.Oid oidConfig : oids) {

            String oid = normalizeOid(oidConfig.getOidPattern());
            String metricType = oidConfig.getMetricType();

            try {

                if (!"TABLE_COLUMN".equalsIgnoreCase(
                        oidConfig.getOidKind())) {

                    log.warn(
                            "Skip non TABLE_COLUMN ARP OID: device={}, oid={}",
                            deviceId, oid
                    );

                    discoverySuccess = false;
                    continue;
                }

                boolean macColumn = isMacOid(oid, metricType);
                boolean ipColumn = isIpOid(oid, metricType);

                if (!macColumn && !ipColumn) {
                    log.warn(
                            "Unknown ARP column: device={}, metricType={}, oid={}",
                            deviceId, metricType, oid
                    );
                    discoverySuccess = false;
                    continue;
                }

                WalkResult walkResult = snmpClient.walk(
                        config.getIpAddress(),
                        config.getCredentials(),
                        oid
                );

                if (walkResult == null || !walkResult.success()) {

                    discoverySuccess = false;

                    log.warn(
                            "ARP WALK failed: device={}, oid={}",
                            deviceId, oid
                    );

                    continue;
                }

                Map<String, Object> values = walkResult.values();

                if (values == null || values.isEmpty()) {

                    log.warn(
                            "ARP WALK returned empty: device={}, oid={}",
                            deviceId, oid
                    );

                    continue;
                }

                log.info(
                        "ARP WALK success: device={}, oid={}, count={}",
                        deviceId, oid, values.size()
                );

                for (Map.Entry<String, Object> entry :
                        values.entrySet()) {

                    // Xử lý cả iso.3.6... và 1.3.6...
                    String suffix = getOidSuffix(
                            oid,
                            entry.getKey()
                    );

                    if (suffix == null) {
                        log.debug(
                                "Cannot extract OID suffix: base={}, full={}",
                                oid, entry.getKey()
                        );
                        continue;
                    }

                    ArpIndex index = parseArpIndex(suffix);

                    if (index == null) {
                        log.debug(
                                "Invalid ARP index: device={}, suffix={}",
                                deviceId, suffix
                        );
                        continue;
                    }

                    String key = index.interfaceIndex()
                            + "|" + index.ipAddress();

                    Map<String, Object> arpEntry =
                            arpEntries.computeIfAbsent(
                                    key,
                                    k -> {
                                        Map<String, Object> item =
                                                new LinkedHashMap<>();

                                        item.put(
                                                "ipAddress",
                                                index.ipAddress()
                                        );

                                        return item;
                                    }
                            );

                    if (macColumn) {

                        String macAddress =
                                convertMacAddress(entry.getValue());

                        if (macAddress != null) {
                            arpEntry.put(
                                    "macAddress",
                                    macAddress
                            );
                        }

                    } else if (ipColumn) {

                        // IP đã được lấy từ OID index.
                        // Không cần lấy giá trị cột IP.
                        arpEntry.put(
                                "ipAddress",
                                index.ipAddress()
                        );
                    }
                }

            } catch (Exception e) {

                discoverySuccess = false;

                log.error(
                        "ARP polling error: device={}, oid={}",
                        deviceId, oid, e
                );
            }
        }

        result.put(
                "arpEntries",
                arpEntries.values()
                        .stream()
                        .map(item -> {
                            Map<String, Object> output =
                                    new LinkedHashMap<>();

                            output.put(
                                    "ipAddress",
                                    item.get("ipAddress")
                            );

                            output.put(
                                    "macAddress",
                                    item.get("macAddress")
                            );

                            return output;
                        })
                        .toList()
        );

        result.put("discoverySuccess", discoverySuccess);

        log.info(
                "ARP polling completed: device={}, entries={}",
                deviceId, arpEntries.size()
        );

        return result;
    }

    /**
     * Nhận diện cột MAC.
     *
     * Cisco/Fortinet: IP-MIB ipNetToMediaPhysAddress
     * Peplink: RFC1213 atPhysAddress
     */
    private boolean isMacOid(String oid, String metricType) {

        String type = metricType == null
                ? ""
                : metricType.toLowerCase(Locale.ROOT);

        return type.contains("mac")
                || type.contains("physaddress")
                || oid.endsWith(".4.22.1.2")
                || oid.endsWith(".3.1.1.2");
    }

    /**
     * Nhận diện cột IP.
     *
     * Cisco/Fortinet: IP-MIB ipNetToMediaNetAddress
     * Peplink: RFC1213 atNetAddress
     */
    private boolean isIpOid(String oid, String metricType) {

        String type = metricType == null
                ? ""
                : metricType.toLowerCase(Locale.ROOT);

        return type.contains("ip")
                || type.contains("netaddress")
                || oid.endsWith(".4.22.1.3")
                || oid.endsWith(".3.1.1.3");
    }

    /**
     * Chuẩn hóa:
     *
     * iso.3.6.1.2.1.4.22.1.2
     * 3.6.1.2.1.4.22.1.2
     * .1.3.6.1.2.1.4.22.1.2
     */
    private String normalizeOid(String oid) {

        if (oid == null || oid.isBlank()) {
            throw new IllegalArgumentException(
                    "OID must not be null or blank"
            );
        }

        String normalized = oid.trim();

        if (normalized.startsWith("iso.")) {
            normalized = "1." + normalized.substring(4);
        }

        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }

        return normalized;
    }

    /**
     * Lấy phần index phía sau base OID.
     */
    private String getOidSuffix(
            String baseOid,
            String fullOid) {

        if (baseOid == null || fullOid == null) {
            return null;
        }

        String normalizedBase = normalizeOid(baseOid);
        String normalizedFull = normalizeOid(fullOid);

        String prefix = normalizedBase + ".";

        if (!normalizedFull.startsWith(prefix)) {
            return null;
        }

        return normalizedFull.substring(prefix.length());
    }

    /**
     * Cisco/Fortinet:
     * ifIndex.IP1.IP2.IP3.IP4
     *
     * Ví dụ:
     * 14.172.16.200.66
     *
     * Peplink:
     * ifIndex.ExtraIndex.IP1.IP2.IP3.IP4
     *
     * Ví dụ:
     * 15.1.172.16.200.65
     */
    private ArpIndex parseArpIndex(String suffix) {

        if (suffix == null || suffix.isBlank()) {
            return null;
        }

        String[] parts = suffix.split("\\.");

        // Tối thiểu: ifIndex + 4 octet IP
        if (parts.length < 5) {
            return null;
        }

        try {

            int interfaceIndex = Integer.parseInt(parts[0]);

            if (interfaceIndex <= 0) {
                return null;
            }

            int ipStart = parts.length - 4;

            String ipAddress = String.join(
                    ".",
                    Arrays.copyOfRange(
                            parts,
                            ipStart,
                            parts.length
                    )
            );

            if (!isValidIp(ipAddress)) {
                return null;
            }

            return new ArpIndex(
                    interfaceIndex,
                    ipAddress
            );

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isValidIp(String ipAddress) {

        String[] octets = ipAddress.split("\\.");

        if (octets.length != 4) {
            return false;
        }

        try {

            for (String octet : octets) {

                int value = Integer.parseInt(octet);

                if (value < 0 || value > 255) {
                    return false;
                }
            }

            return true;

        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Chuyển MAC từ SNMP Hex-STRING thành dạng:
     *
     * 00:11:22:33:44:55
     *
     * Hỗ trợ:
     * - byte[]
     * - Hex-STRING: 00 11 22 33 44 55
     * - 00:11:22:33:44:55
     */
    private static final java.util.regex.Pattern MAC_PATTERN =
        java.util.regex.Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");

    private String convertMacAddress(Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof byte[] bytes) {
            if (bytes.length != 6) {
                log.warn("Invalid MAC byte length from SNMP: length={}", bytes.length);
                return null;
            }
            return formatMacBytes(bytes);
        }

        String rawString = rawValue.toString();

        if (rawString.length() == 6) {
            byte[] asciiBytes = rawString.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            return formatMacBytes(asciiBytes);
        }

        String value = rawString.trim()
                .replaceFirst("(?i)^Hex-STRING:\\s*", "")
                .replaceAll("[:-]", " ");

        String[] parts = value.trim().split("\\s+");

        if (parts.length != 6) {
            log.warn("Invalid MAC value from SNMP: {}", rawValue);
            return null;
        }

        try {
            byte[] parsedBytes = new byte[6];
            for (int i = 0; i < 6; i++) {
                int number = Integer.parseInt(parts[i], 16);
                if (number < 0 || number > 255) {
                    return null;
                }
                parsedBytes[i] = (byte) number;
            }
            String formatted = formatMacBytes(parsedBytes);
            return MAC_PATTERN.matcher(formatted).matches() ? formatted : null;
        } catch (NumberFormatException e) {
            log.warn("Cannot parse MAC hex from SNMP: {}", rawValue);
            return null;
        }
    }

    private String formatMacBytes(byte[] bytes) {
        StringBuilder mac = new StringBuilder(17);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                mac.append(":");
            }
            mac.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return mac.toString();
    }


    private record ArpIndex(
            int interfaceIndex,
            String ipAddress
    ) {}
}

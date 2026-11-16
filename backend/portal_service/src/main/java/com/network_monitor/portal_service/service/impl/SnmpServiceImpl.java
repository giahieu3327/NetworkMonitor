
package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.metric.MetricConfig;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.Metric;
import com.network_monitor.portal_service.repository.MetricRepository;
import com.network_monitor.portal_service.service.SnmpConfigCacheService;
import com.network_monitor.portal_service.service.SnmpService;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnmpServiceImpl implements SnmpService {

    private static final int SNMP_TIMEOUT = 3000;
    private static final int SNMP_RETRIES = 1;

    // Metric scopes
    private static final String SCOPE_DEVICE = "DEVICE";
    private static final String SCOPE_INTERFACE = "INTERFACE";
    private static final String SCOPE_METRIC = "METRIC";
    private static final String SCOPE_ARP = "ARP";

    // OID kinds
    private static final String OID_KIND_SINGLE = "SINGLE";
    private static final String OID_KIND_TABLE_COLUMN = "TABLE_COLUMN";

    private final MetricRepository metricRepository;
    private final EntityManager entityManager;
    private final SnmpConfigCacheService configCacheService;

    // =====================================================
    // CONFIGURATION MANAGEMENT
    // =====================================================

    @Override
    public void loadConfigs() {
        configCacheService.loadConfigs();
    }

    @Override
    public void reloadConfigs() {
        configCacheService.reloadConfigs();
    }

    @Override
    public Map<String, Object> getDeviceConfigs() {
        return configCacheService.getDeviceConfigs();
    }

    private MetricConfig getDeviceConfig(Long deviceId) {

        if (deviceId == null) {
            throw new IllegalArgumentException(
                    "Device ID must not be null"
            );
        }

        return configCacheService.getDeviceConfig(deviceId);
    }

    // =====================================================
    // GET OIDS BY SCOPE
    // =====================================================

    private List<MetricConfig.Oid> getOidsByScope(
            MetricConfig config,
            String scope
    ) {

        if (config == null || config.getOids() == null) {
            return new ArrayList<>();
        }

        return config.getOids()
                .stream()
                .filter(Objects::nonNull)
                .filter(oid ->
                        scope.equalsIgnoreCase(oid.getMetricScope())
                )
                .toList();
    }

    // =====================================================
    // VALIDATE OID KIND
    // =====================================================

    private String getOidKind(MetricConfig.Oid oidConfig) {

        if (oidConfig == null || oidConfig.getOidKind() == null) {
            throw new IllegalArgumentException(
                    "OID kind must not be null"
            );
        }

        String oidKind = oidConfig.getOidKind()
                .trim()
                .toUpperCase();

        return switch (oidKind) {

            case OID_KIND_SINGLE ->
                    OID_KIND_SINGLE;

            case OID_KIND_TABLE_COLUMN ->
                    OID_KIND_TABLE_COLUMN;

            default -> throw new IllegalArgumentException(
                    "Unsupported OID kind: " + oidKind
            );
        };
    }

    // =====================================================
    // BUILD INTERFACE INSTANCE OID
    // =====================================================

    /**
     * Build a specific interface instance OID.
     *
     * Example:
     * Base OID: 1.3.6.1.2.1.2.2.1.10
     * Interface index: 2
     * Result: 1.3.6.1.2.1.2.2.1.10.2
     */
    private String buildInterfaceInstanceOid(
            String baseOid,
            Integer interfaceIndex
    ) {

        if (baseOid == null || baseOid.isBlank()) {
            throw new IllegalArgumentException(
                    "OID pattern must not be blank"
            );
        }

        if (interfaceIndex == null || interfaceIndex < 0) {
            throw new IllegalArgumentException(
                    "Valid interface index is required"
            );
        }

        String suffix = "." + interfaceIndex;

        // Avoid appending the index twice.
        if (baseOid.endsWith(suffix)) {
            return baseOid;
        }

        return baseOid + suffix;
    }

    // =====================================================
    // DEVICE SCOPE
    // =====================================================

    @Override
    public Map<String, Object> pollDeviceScope(Long deviceId) {

        MetricConfig config = getDeviceConfig(deviceId);

        List<MetricConfig.Oid> oids =
                getOidsByScope(config, SCOPE_DEVICE);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("deviceId", deviceId);
        result.put("deviceName", config.getDeviceName());
        result.put("ipAddress", config.getIpAddress());
        result.put("deviceType", config.getDeviceType());

        Map<String, Object> deviceData = new LinkedHashMap<>();

        for (MetricConfig.Oid oidConfig : oids) {

            String oid = oidConfig.getOidPattern();
            String metricType = oidConfig.getMetricType();

            try {

                String oidKind = getOidKind(oidConfig);

                if (OID_KIND_SINGLE.equals(oidKind)) {

                    // SINGLE -> SNMP GET
                    Object value = getValue(config, oid);

                    if (value != null) {

                        deviceData.put(
                                metricType,
                                convertValue(value, oidConfig)
                        );
                    }

                } else {

                    // TABLE_COLUMN -> SNMP WALK
                    Map<String, Object> values =
                            walk(config, oid);

                    Map<String, Object> tableData =
                            new LinkedHashMap<>();

                    for (Map.Entry<String, Object> entry :
                            values.entrySet()) {

                        String suffix = getOidSuffix(
                                oid,
                                entry.getKey()
                        );

                        tableData.put(
                                suffix != null
                                        ? suffix
                                        : entry.getKey(),
                                convertValue(
                                        entry.getValue(),
                                        oidConfig
                                )
                        );
                    }

                    if (!tableData.isEmpty()) {

                        deviceData.put(
                                metricType,
                                tableData
                        );
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

        log.debug(
                "DEVICE polling completed for device {}",
                deviceId
        );

        return result;
    }

    // =====================================================
    // INTERFACE SCOPE
    // =====================================================

    @Override
    public Map<String, Object> pollInterfaceScope(Long deviceId) {

        MetricConfig config = getDeviceConfig(deviceId);

        List<MetricConfig.Oid> oids =
                getOidsByScope(config, SCOPE_INTERFACE);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("deviceId", deviceId);
        result.put("deviceName", config.getDeviceName());

        Map<String, Map<String, Object>> interfaces =
                new LinkedHashMap<>();

        for (MetricConfig.Oid oidConfig : oids) {

            String oid = oidConfig.getOidPattern();
            String metricType = oidConfig.getMetricType();

            try {

                String oidKind = getOidKind(oidConfig);

                // -------------------------------------------------
                // SINGLE -> SNMP GET
                // -------------------------------------------------

                if (OID_KIND_SINGLE.equals(oidKind)) {

                    Integer interfaceIndex =
                            oidConfig.getInterfaceIndex();

                    if (interfaceIndex == null) {

                        log.warn(
                                "SINGLE INTERFACE OID requires interfaceIndex: device={}, metric={}, oid={}",
                                deviceId,
                                metricType,
                                oid
                        );

                        continue;
                    }

                    String instanceOid =
                            buildInterfaceInstanceOid(
                                    oid,
                                    interfaceIndex
                            );

                    Object value = getValue(
                            config,
                            instanceOid
                    );

                    if (value == null) {
                        continue;
                    }

                    String index =
                            String.valueOf(interfaceIndex);

                    interfaces
                            .computeIfAbsent(
                                    index,
                                    key -> new LinkedHashMap<>()
                            )
                            .put(
                                    metricType,
                                    convertValue(value, oidConfig)
                            );

                    continue;
                }

                // -------------------------------------------------
                // TABLE_COLUMN -> SNMP WALK
                // -------------------------------------------------

                Map<String, Object> values =
                        walk(config, oid);

                for (Map.Entry<String, Object> entry :
                        values.entrySet()) {

                    String suffix = getOidSuffix(
                            oid,
                            entry.getKey()
                    );

                    if (suffix == null || suffix.isBlank()) {
                        continue;
                    }

                    // If interface_index is specified,
                    // only keep that interface.
                    if (oidConfig.getInterfaceIndex() != null &&
                            !suffix.equals(
                                    String.valueOf(
                                            oidConfig.getInterfaceIndex()
                                    )
                            )) {

                        continue;
                    }

                    interfaces
                            .computeIfAbsent(
                                    suffix,
                                    key -> new LinkedHashMap<>()
                            )
                            .put(
                                    metricType,
                                    convertValue(
                                            entry.getValue(),
                                            oidConfig
                                    )
                            );
                }

            } catch (Exception e) {

                log.warn(
                        "INTERFACE polling failed: device={}, metric={}, oid={}",
                        deviceId,
                        metricType,
                        oid,
                        e
                );
            }
        }

        result.put("interfaces", interfaces);

        log.debug(
                "INTERFACE polling completed for device {}",
                deviceId
        );

        return result;
    }

    // =====================================================
    // METRIC SCOPE - SCHEDULED POLLING
    // =====================================================

    @Override
    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Ho_Chi_Minh"
    )
    public void pollMetricScope() {

        List<MetricConfig> configs =
                configCacheService.getAllDeviceConfigs();

        if (configs == null || configs.isEmpty()) {

            log.warn(
                    "No SNMP device configurations available"
            );

            return;
        }

        log.info(
                "Starting scheduled METRIC polling for {} devices",
                configs.size()
        );

        for (MetricConfig config : configs) {

            try {

                pollMetricForDevice(config);

            } catch (Exception e) {

                log.error(
                        "METRIC polling failed for device {}",
                        config.getDeviceId(),
                        e
                );
            }
        }

        log.info("Finished scheduled METRIC polling");
    }

    // =====================================================
    // POLL METRIC FOR ONE DEVICE
    // =====================================================

    private void pollMetricForDevice(MetricConfig config) {

        List<MetricConfig.Oid> oids =
                getOidsByScope(config, SCOPE_METRIC);

        if (oids.isEmpty()) {
            return;
        }

        Map<String, Object> metricData =
                new LinkedHashMap<>();

        for (MetricConfig.Oid oidConfig : oids) {

            String oid = oidConfig.getOidPattern();
            String metricType = oidConfig.getMetricType();

            try {

                String oidKind = getOidKind(oidConfig);

                // -------------------------------------------------
                // SINGLE -> SNMP GET
                // -------------------------------------------------

                if (OID_KIND_SINGLE.equals(oidKind)) {

                    Object value = getValue(config, oid);

                    if (value != null) {

                        metricData.put(
                                metricType,
                                convertValue(value, oidConfig)
                        );
                    }

                } else {

                    // -------------------------------------------------
                    // TABLE_COLUMN -> SNMP WALK
                    // -------------------------------------------------

                    Map<String, Object> values =
                            walk(config, oid);

                    Map<String, Object> tableData =
                            new LinkedHashMap<>();

                    for (Map.Entry<String, Object> entry :
                            values.entrySet()) {

                        String suffix = getOidSuffix(
                                oid,
                                entry.getKey()
                        );

                        tableData.put(
                                suffix != null
                                        ? suffix
                                        : entry.getKey(),
                                convertValue(
                                        entry.getValue(),
                                        oidConfig
                                )
                        );
                    }

                    if (!tableData.isEmpty()) {

                        metricData.put(
                                metricType,
                                tableData
                        );
                    }
                }

            } catch (Exception e) {

                log.warn(
                        "METRIC polling failed: device={}, metric={}, oid={}",
                        config.getDeviceId(),
                        metricType,
                        oid,
                        e
                );
            }
        }

        if (metricData.isEmpty()) {

            log.warn(
                    "No METRIC data collected for device {}",
                    config.getDeviceId()
            );

            return;
        }

        saveMetric(config, metricData);
    }

    // =====================================================
    // SAVE METRIC TO POSTGRESQL
    // =====================================================

    private void saveMetric(
            MetricConfig config,
            Map<String, Object> metricData
    ) {

        Metric metric = new Metric();

        Device device = entityManager.getReference(
                Device.class,
                config.getDeviceId()
        );

        metric.setDevice(device);
        metric.setMetricData(metricData);
        metric.setTimestamp(OffsetDateTime.now());

        metricRepository.saveAndFlush(metric);

        log.debug(
                "Saved METRIC data for device {}",
                config.getDeviceId()
        );
    }

    // =====================================================
    // ARP SCOPE
    // =====================================================

    @Override
    public Map<String, Object> pollArpScope(Long deviceId) {

        MetricConfig config = getDeviceConfig(deviceId);

        List<MetricConfig.Oid> oids =
                getOidsByScope(config, SCOPE_ARP);

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("deviceId", deviceId);
        result.put("deviceName", config.getDeviceName());

        Map<String, Map<String, Object>> arpEntries =
                new LinkedHashMap<>();

        for (MetricConfig.Oid oidConfig : oids) {

            String oid = oidConfig.getOidPattern();
            String metricType = oidConfig.getMetricType();

            try {

                // ARP table OIDs must use TABLE_COLUMN.
                String oidKind = getOidKind(oidConfig);

                if (!OID_KIND_TABLE_COLUMN.equals(oidKind)) {

                    log.warn(
                            "ARP OID must use TABLE_COLUMN: device={}, metric={}, oid={}, oidKind={}",
                            deviceId,
                            metricType,
                            oid,
                            oidKind
                    );

                    continue;
                }

                Map<String, Object> values =
                        walk(config, oid);

                for (Map.Entry<String, Object> entry :
                        values.entrySet()) {

                    String fullOid = entry.getKey();

                    String suffix = getOidSuffix(
                            oid,
                            fullOid
                    );

                    if (suffix == null) {
                        continue;
                    }

                    String ipAddress =
                            parseArpIpAddress(oid, suffix);

                    if (ipAddress == null) {

                        log.debug(
                                "Cannot parse ARP index: oid={}, suffix={}",
                                oid,
                                suffix
                        );

                        continue;
                    }

                    arpEntries
                            .computeIfAbsent(
                                    ipAddress,
                                    key -> new LinkedHashMap<>()
                            )
                            .put(
                                    metricType,
                                    convertValue(
                                            entry.getValue(),
                                            oidConfig
                                    )
                            );
                }

            } catch (Exception e) {

                log.warn(
                        "ARP polling failed: device={}, metric={}, oid={}",
                        deviceId,
                        metricType,
                        oid,
                        e
                );
            }
        }

        result.put("arpEntries", arpEntries);

        log.debug(
                "ARP polling completed for device {}",
                deviceId
        );

        return result;
    }

    // =====================================================
    // PARSE ARP IP ADDRESS
    // =====================================================

    private String parseArpIpAddress(
            String baseOid,
            String suffix
    ) {

        String[] parts = suffix.split("\\.");

        // IP-MIB: ipNetToMediaTable
        if (baseOid.startsWith("1.3.6.1.2.1.3.1.1.")) {

            try {

                if (parts.length < 3) {
                    return null;
                }

                int macLength =
                        Integer.parseInt(parts[1]);

                int ipLengthPosition =
                        2 + macLength;

                if (parts.length <= ipLengthPosition) {
                    return null;
                }

                int ipLength =
                        Integer.parseInt(parts[ipLengthPosition]);

                if (ipLength != 4) {
                    return null;
                }

                int ipStart = ipLengthPosition + 1;

                if (parts.length < ipStart + 4) {
                    return null;
                }

                return String.join(
                        ".",
                        parts[ipStart],
                        parts[ipStart + 1],
                        parts[ipStart + 2],
                        parts[ipStart + 3]
                );

            } catch (NumberFormatException e) {

                return null;
            }
        }

        // IP-MIB: ipNetToMediaPhysAddress / ipNetToMediaType
        if (baseOid.startsWith("1.3.6.1.2.1.4.22.1.")) {

            if (parts.length != 5) {
                return null;
            }

            return String.join(
                    ".",
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4]
            );
        }

        return null;
    }

    // =====================================================
    // SNMP GET - TRY MULTIPLE CREDENTIALS
    // =====================================================

    private Object getValue(
            MetricConfig config,
            String oid
    ) {

        if (config.getCredentials() == null ||
                config.getCredentials().isEmpty()) {

            return null;
        }

        for (MetricConfig.Credential credential :
                config.getCredentials()) {

            if (!isUsable(credential)) {
                continue;
            }

            try {

                Object value = sendGet(
                        config.getIpAddress(),
                        credential,
                        oid
                );

                if (value != null) {
                    return value;
                }

            } catch (Exception e) {

                log.debug(
                        "SNMP GET failed: host={}, protocol={}, oid={}",
                        config.getIpAddress(),
                        credential.getProtocolType(),
                        oid,
                        e
                );
            }
        }

        return null;
    }

    // =====================================================
    // SEND SNMP GET
    // =====================================================

    private Object sendGet(
            String host,
            MetricConfig.Credential credential,
            String oid
    ) throws IOException {

        Address address = GenericAddress.parse(
                "udp:" + host + "/" + credential.getPort()
        );

        DefaultUdpTransportMapping transport =
                new DefaultUdpTransportMapping();

        Snmp snmp = new Snmp(transport);

        try {

            transport.listen();

            CommunityTarget<Address> target =
                    createTarget(address, credential);

            PDU pdu = new PDU();

            pdu.add(
                    new VariableBinding(new OID(oid))
            );

            pdu.setType(PDU.GET);

            ResponseEvent<Address> event =
                    snmp.get(pdu, target);

            if (event == null ||
                    event.getResponse() == null ||
                    event.getResponse().getErrorStatus()
                            != PDU.noError) {

                return null;
            }

            PDU response = event.getResponse();

            if (response.size() == 0) {
                return null;
            }

            VariableBinding binding =
                    response.get(0);

            if (binding == null ||
                    binding.isException()) {

                return null;
            }

            return binding.getVariable().toString();

        } finally {

            snmp.close();
            transport.close();
        }
    }

    // =====================================================
    // SNMP WALK - TRY MULTIPLE CREDENTIALS
    // =====================================================

    private Map<String, Object> walk(
            MetricConfig config,
            String oid
    ) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        if (config.getCredentials() == null ||
                config.getCredentials().isEmpty()) {

            return result;
        }

        for (MetricConfig.Credential credential :
                config.getCredentials()) {

            if (!isUsable(credential)) {
                continue;
            }

            try {

                result = sendWalk(
                        config.getIpAddress(),
                        credential,
                        oid
                );

                if (!result.isEmpty()) {
                    return result;
                }

            } catch (Exception e) {

                log.debug(
                        "SNMP WALK failed: host={}, protocol={}, oid={}",
                        config.getIpAddress(),
                        credential.getProtocolType(),
                        oid,
                        e
                );
            }
        }

        return result;
    }

    // =====================================================
    // SEND SNMP WALK
    // =====================================================

    private Map<String, Object> sendWalk(
            String host,
            MetricConfig.Credential credential,
            String rootOid
    ) throws IOException {

        Map<String, Object> result =
                new LinkedHashMap<>();

        Address address = GenericAddress.parse(
                "udp:" + host + "/" + credential.getPort()
        );

        DefaultUdpTransportMapping transport =
                new DefaultUdpTransportMapping();

        Snmp snmp = new Snmp(transport);

        try {

            transport.listen();

            CommunityTarget<Address> target =
                    createTarget(address, credential);

            OID root = new OID(rootOid);

            OID currentOid = root;

            int maxRows = 10000;

            for (int i = 0; i < maxRows; i++) {

                PDU pdu = new PDU();

                pdu.add(
                        new VariableBinding(currentOid)
                );

                pdu.setType(PDU.GETNEXT);

                ResponseEvent<Address> event =
                        snmp.getNext(pdu, target);

                if (event == null ||
                        event.getResponse() == null) {

                    break;
                }

                PDU response = event.getResponse();

                if (response.getErrorStatus() != PDU.noError ||
                        response.size() == 0) {

                    break;
                }

                VariableBinding binding =
                        response.get(0);

                if (binding == null ||
                        binding.isException()) {

                    break;
                }

                OID returnedOid = binding.getOid();

                if (returnedOid == null ||
                        !returnedOid.startsWith(root)) {

                    break;
                }

                if (returnedOid.equals(currentOid)) {
                    break;
                }

                result.put(
                        returnedOid.toDottedString(),
                        binding.getVariable().toString()
                );

                currentOid = returnedOid;
            }

        } finally {

            snmp.close();
            transport.close();
        }

        return result;
    }

    // =====================================================
    // CREATE SNMP TARGET
    // =====================================================

    private CommunityTarget<Address> createTarget(
            Address address,
            MetricConfig.Credential credential
    ) {

        CommunityTarget<Address> target =
                new CommunityTarget<>();

        target.setAddress(address);

        target.setCommunity(
                new OctetString(
                        credential.getCommunityString()
                )
        );

        target.setVersion(
                getSnmpVersion(
                        credential.getProtocolType()
                )
        );

        target.setTimeout(SNMP_TIMEOUT);
        target.setRetries(SNMP_RETRIES);

        return target;
    }

    // =====================================================
    // GET SNMP VERSION
    // =====================================================

    private int getSnmpVersion(String protocolType) {

        if (protocolType == null) {

            throw new IllegalArgumentException(
                    "SNMP protocol type must not be null"
            );
        }

        return switch (protocolType.toUpperCase()) {

            case "SNMPV1", "V1" ->
                    SnmpConstants.version1;

            case "SNMPV2C", "V2C" ->
                    SnmpConstants.version2c;

            default -> throw new IllegalArgumentException(
                    "Unsupported SNMP protocol: " + protocolType
            );
        };
    }

    // =====================================================
    // CHECK CREDENTIAL
    // =====================================================

    private boolean isUsable(
            MetricConfig.Credential credential
    ) {

        if (credential == null ||
                credential.getProtocolType() == null ||
                credential.getPort() == null ||
                credential.getPort() <= 0 ||
                credential.getPort() > 65535) {

            return false;
        }

        String protocol =
                credential.getProtocolType().toUpperCase();

        if (!protocol.equals("SNMPV1") &&
                !protocol.equals("V1") &&
                !protocol.equals("SNMPV2C") &&
                !protocol.equals("V2C")) {

            return false;
        }

        return credential.getCommunityString() != null &&
                !credential.getCommunityString().isBlank();
    }

    // =====================================================
    // CONVERT SNMP VALUE
    // =====================================================

    private Object convertValue(
            Object rawValue,
            MetricConfig.Oid oidConfig
    ) {

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

                case "INTEGER",
                     "INTEGER32",
                     "GAUGE",
                     "GAUGE32",
                     "COUNTER",
                     "COUNTER32",
                     "COUNTER64",
                     "UNSIGNED32" -> {

                    double number =
                            Double.parseDouble(value);

                    yield number * multiplier;
                }

                case "TIMETICKS" -> {

                    double ticks =
                            Double.parseDouble(value);

                    yield (ticks / 100.0) * multiplier;
                }

                case "STRING",
                     "OCTETSTRING",
                     "DISPLAYSTRING",
                     "IPADDRESS" -> value;

                default -> value;
            };

        } catch (NumberFormatException e) {

            log.debug(
                    "Cannot convert SNMP value: value={}, type={}",
                    value,
                    dataType
            );

            return value;
        }
    }

    // =====================================================
    // GET OID SUFFIX
    // =====================================================

    private String getOidSuffix(
            String baseOid,
            String fullOid
    ) {

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

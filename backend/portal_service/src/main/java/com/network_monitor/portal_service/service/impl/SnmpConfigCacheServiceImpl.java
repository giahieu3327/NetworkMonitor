
package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.MetricMapper;
import com.network_monitor.portal_service.model.dto.metric.MetricConfig;
import com.network_monitor.portal_service.service.SnmpConfigCacheService;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnmpConfigCacheServiceImpl
        implements SnmpConfigCacheService {

    private static final String DEVICE_KEY_PREFIX =
            "network-monitor:snmp:device:";

    private static final String DEVICE_INDEX_KEY =
            "network-monitor:snmp:devices";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final MetricMapper metricMapper;

    @PostConstruct
    @Override
    public void loadConfigs() {
        reloadConfigs();
    }

    // =====================================================
    // RELOAD CONFIGURATIONS
    // =====================================================

    @Override
    public synchronized void reloadConfigs() {

        try {

            log.info("Starting SNMP configuration reload");

            // 1. Load configurations from PostgreSQL.
            List<MetricConfig> configs = loadMetricConfigs();

            // 2. Get existing device IDs from Redis.
            Set<String> oldDeviceIds =
                    redisTemplate.opsForSet()
                            .members(DEVICE_INDEX_KEY);

            // 3. Delete old device cache.
            if (oldDeviceIds != null && !oldDeviceIds.isEmpty()) {

                List<String> oldKeys = oldDeviceIds.stream()
                        .map(id -> DEVICE_KEY_PREFIX + id)
                        .toList();

                redisTemplate.delete(oldKeys);
            }

            // 4. Clear old device index.
            redisTemplate.delete(DEVICE_INDEX_KEY);

            // 5. Save new configurations.
            for (MetricConfig config : configs) {

                if (config.getDeviceId() == null) {
                    continue;
                }

                saveDeviceConfig(config);
            }

            log.info(
                    "Successfully reloaded {} SNMP device configurations",
                    configs.size()
            );

        } catch (Exception e) {

            log.error(
                    "Failed to reload SNMP configurations from PostgreSQL to Redis",
                    e
            );

            throw new IllegalStateException(
                    "Unable to reload SNMP configurations",
                    e
            );
        }
    }

    // =====================================================
    // LOAD CONFIGURATIONS FROM DATABASE
    // =====================================================

    private List<MetricConfig> loadMetricConfigs() {

        List<MetricConfig> configs =
                metricMapper.findDevicesForMetrics();

        if (configs == null || configs.isEmpty()) {
            return new ArrayList<>();
        }

        for (MetricConfig config : configs) {

            Long deviceId = config.getDeviceId();

            List<MetricConfig.Credential> credentials =
                    metricMapper.findActiveSnmpCredentials(deviceId);

            List<MetricConfig.Oid> oids =
                    metricMapper.findActiveOidConfigs(deviceId);

            config.setCredentials(
                    credentials != null
                            ? credentials
                            : new ArrayList<>()
            );

            config.setOids(
                    oids != null
                            ? oids
                            : new ArrayList<>()
            );
        }

        return configs;
    }

    // =====================================================
    // SAVE ONE DEVICE CONFIGURATION
    // =====================================================

    private void saveDeviceConfig(MetricConfig config) {

        try {

            Long deviceId = config.getDeviceId();

            String key = DEVICE_KEY_PREFIX + deviceId;

            String json = objectMapper.writeValueAsString(config);

            // Save JSON configuration.
            redisTemplate.opsForValue().set(key, json);

            // Maintain device ID index.
            redisTemplate.opsForSet().add(
                    DEVICE_INDEX_KEY,
                    String.valueOf(deviceId)
            );

            log.debug(
                    "Cached SNMP configuration: deviceId={}, key={}",
                    deviceId,
                    key
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to save SNMP configuration to Redis",
                    e
            );
        }
    }

    // =====================================================
    // GET ONE DEVICE CONFIGURATION
    // =====================================================

    @Override
    public MetricConfig getDeviceConfig(Long deviceId) {

        if (deviceId == null) {
            throw new IllegalArgumentException(
                    "Device ID must not be null"
            );
        }

        String key = DEVICE_KEY_PREFIX + deviceId;

        try {

            String json = redisTemplate.opsForValue().get(key);

            if (json != null && !json.isBlank()) {

                MetricConfig config =
                        objectMapper.readValue(
                                json,
                                MetricConfig.class
                        );

                log.debug(
                        "Loaded SNMP configuration from Redis: deviceId={}",
                        deviceId
                );

                return config;
            }

            log.warn(
                    "SNMP configuration not found in Redis: deviceId={}",
                    deviceId
            );

            // Redis cache miss: reload from PostgreSQL.
            reloadConfigs();

            json = redisTemplate.opsForValue().get(key);

            if (json != null && !json.isBlank()) {

                return objectMapper.readValue(
                        json,
                        MetricConfig.class
                );
            }

        } catch (Exception e) {

            log.error(
                    "Redis GET failed for device {}, falling back to PostgreSQL",
                    deviceId,
                    e
            );
        }

        // Redis unavailable or cache miss after reload.
        return loadDeviceConfigFromDatabase(deviceId);
    }

    // =====================================================
    // FALLBACK TO POSTGRESQL
    // =====================================================

    private MetricConfig loadDeviceConfigFromDatabase(Long deviceId) {

        List<MetricConfig> configs = loadMetricConfigs();

        return configs.stream()
                .filter(config ->
                        Objects.equals(
                                config.getDeviceId(),
                                deviceId
                        )
                )
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "SNMP configuration not found for device ID: "
                                + deviceId
                ));
    }

    // =====================================================
    // GET ALL DEVICE CONFIGURATIONS
    // =====================================================

    @Override
    public List<MetricConfig> getAllDeviceConfigs() {

        try {

            Set<String> deviceIds =
                    redisTemplate.opsForSet()
                            .members(DEVICE_INDEX_KEY);

            if (deviceIds == null || deviceIds.isEmpty()) {

                log.warn(
                        "SNMP device index not found in Redis. Reloading..."
                );

                reloadConfigs();

                deviceIds =
                        redisTemplate.opsForSet()
                                .members(DEVICE_INDEX_KEY);
            }

            List<MetricConfig> configs = new ArrayList<>();

            if (deviceIds != null) {

                for (String deviceId : deviceIds) {

                    String key = DEVICE_KEY_PREFIX + deviceId;

                    String json =
                            redisTemplate.opsForValue().get(key);

                    if (json == null || json.isBlank()) {

                        log.warn(
                                "Missing Redis configuration for device {}",
                                deviceId
                        );

                        continue;
                    }

                    MetricConfig config =
                            objectMapper.readValue(
                                    json,
                                    MetricConfig.class
                            );

                    configs.add(config);
                }
            }

            // Detect incomplete cache.
            if (configs.size() != (deviceIds != null
                    ? deviceIds.size()
                    : 0)) {

                log.warn(
                        "Incomplete SNMP Redis cache. Reloading configurations."
                );

                reloadConfigs();

                return getAllDeviceConfigsAfterReload();
            }

            return configs;

        } catch (Exception e) {

            log.error(
                    "Redis GET ALL failed. Falling back to PostgreSQL",
                    e
            );

            return loadMetricConfigs();
        }
    }

    private List<MetricConfig> getAllDeviceConfigsAfterReload() {

        try {

            Set<String> deviceIds =
                    redisTemplate.opsForSet()
                            .members(DEVICE_INDEX_KEY);

            List<MetricConfig> configs = new ArrayList<>();

            if (deviceIds == null) {
                return configs;
            }

            for (String deviceId : deviceIds) {

                String json = redisTemplate.opsForValue().get(
                        DEVICE_KEY_PREFIX + deviceId
                );

                if (json == null || json.isBlank()) {
                    continue;
                }

                configs.add(
                        objectMapper.readValue(
                                json,
                                MetricConfig.class
                        )
                );
            }

            return configs;

        } catch (Exception e) {

            log.error("Unable to read Redis configurations after reload", e);

            return loadMetricConfigs();
        }
    }

    // =====================================================
    // CONFIGURATION INFORMATION
    // =====================================================

    @Override
    public Map<String, Object> getDeviceConfigs() {

        List<MetricConfig> configs = getAllDeviceConfigs();

        List<Map<String, Object>> devices = new ArrayList<>();

        int totalCredentials = 0;
        int totalOids = 0;

        for (MetricConfig config : configs) {

            Map<String, Object> device = new LinkedHashMap<>();

            device.put("deviceId", config.getDeviceId());
            device.put("deviceName", config.getDeviceName());
            device.put("ipAddress", config.getIpAddress());
            device.put("deviceType", config.getDeviceType());

            List<Map<String, Object>> credentials =
                    new ArrayList<>();

            if (config.getCredentials() != null) {

                for (MetricConfig.Credential credential :
                        config.getCredentials()) {

                    Map<String, Object> item =
                            new LinkedHashMap<>();

                    item.put(
                            "protocolType",
                            credential.getProtocolType()
                    );

                    item.put("port", credential.getPort());

                    // Never expose community string.
                    item.put(
                            "hasCommunityString",
                            credential.getCommunityString() != null
                                    && !credential.getCommunityString().isBlank()
                    );

                    credentials.add(item);
                }
            }

            List<Map<String, Object>> oids =
                    new ArrayList<>();

            if (config.getOids() != null) {

                for (MetricConfig.Oid oid : config.getOids()) {

                    Map<String, Object> item =
                            new LinkedHashMap<>();

                        item.put("metricType", oid.getMetricType());
                        item.put("metricScope", oid.getMetricScope());
                        item.put("oidPattern", oid.getOidPattern());
                        item.put("oidKind", oid.getOidKind());
                        item.put("dataType", oid.getDataType());
                        item.put("multiplier", oid.getMultiplier());
                        item.put("interfaceIndex", oid.getInterfaceIndex());
                        item.put("interfaceName", oid.getInterfaceName());

                    oids.add(item);
                }
            }

            device.put("credentials", credentials);
            device.put("oids", oids);

            device.put("credentialCount", credentials.size());
            device.put("oidCount", oids.size());

            totalCredentials += credentials.size();
            totalOids += oids.size();

            devices.add(device);
        }

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("deviceCount", devices.size());
        response.put("totalCredentials", totalCredentials);
        response.put("totalOids", totalOids);
        response.put("devices", devices);

        return response;
    }
}

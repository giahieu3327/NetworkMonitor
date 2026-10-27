
package com.network_monitor.monitor_service.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.monitor_service.model.dto.kafka.oidconfig.OidConfigEventDto;
import com.network_monitor.monitor_service.service.OidConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class OidConfigServiceImpl implements OidConfigService {

    private final ObjectMapper objectMapper;

    /**
     * Cache OID config hoàn toàn trong RAM.
     *
     * Key: OID config ID (Long).
     * Value: OidConfigEventDto.
     */
    private final AtomicReference<Map<Long, OidConfigEventDto>> oidConfigCache =
            new AtomicReference<>(Map.of());

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public void createOidConfig(Object data) {
        OidConfigEventDto config = convertToOidConfigDto(data);

        putOidConfigToCache(config);

        log.info(
                "Đã thêm OidConfig vào RAM: oidConfigId={}, deviceId={}",
                config.getId(),
                config.getDeviceId()
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    public void updateOidConfig(Object data) {
        OidConfigEventDto config = convertToOidConfigDto(data);

        putOidConfigToCache(config);

        log.info(
                "Đã cập nhật OidConfig trong RAM: oidConfigId={}, deviceId={}",
                config.getId(),
                config.getDeviceId()
        );
    }

    // =========================================================
    // DELETE
    // =========================================================

    @Override
    public void deleteOidConfig(Object data) {
        Long id = extractId(data);

        oidConfigCache.updateAndGet(current -> {
            if (!current.containsKey(id)) {
                return current;
            }

            Map<Long, OidConfigEventDto> updated =
                    new HashMap<>(current);

            updated.remove(id);

            return Map.copyOf(updated);
        });

        log.info("Đã xóa OidConfig khỏi RAM: oidConfigId={}", id);
    }

    // =========================================================
    // QUERY FROM RAM
    // =========================================================

    @Override
    public Optional<OidConfigEventDto> getOidConfigById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(oidConfigCache.get().get(id));
    }

    @Override
    public List<OidConfigEventDto> getOidConfigsByDeviceId(Long deviceId) {
        if (deviceId == null) {
            return List.of();
        }

        List<OidConfigEventDto> result = new ArrayList<>();

        for (OidConfigEventDto config : oidConfigCache.get().values()) {
            if (deviceId.equals(config.getDeviceId())) {
                result.add(config);
            }
        }

        result.sort(Comparator.comparing(OidConfigEventDto::getId));

        return List.copyOf(result);
    }

    @Override
    public List<OidConfigEventDto> getAllOidConfigs() {
        List<OidConfigEventDto> configs =
                new ArrayList<>(oidConfigCache.get().values());

        configs.sort(Comparator.comparing(OidConfigEventDto::getId));

        return configs;
    }

    // =========================================================
    // CONVERT PAYLOAD TO DTO
    // =========================================================

    private OidConfigEventDto convertToOidConfigDto(Object data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "OidConfig payload không được null"
            );
        }

        Map<String, Object> payload = objectMapper.convertValue(
                data,
                new TypeReference<Map<String, Object>>() {}
        );

        // Hỗ trợ payload dùng oidConfigId thay cho id.
        if (!payload.containsKey("id")
                && payload.containsKey("oidConfigId")) {
            payload.put("id", payload.get("oidConfigId"));
        }

        // Hỗ trợ payload chứa object device thay vì deviceId.
        if (!payload.containsKey("deviceId")
                && payload.get("device") instanceof Map<?, ?> deviceMap) {
            payload.put("deviceId", deviceMap.get("id"));
        }

        OidConfigEventDto config = objectMapper.convertValue(
                payload,
                OidConfigEventDto.class
        );

        if (config.getId() == null) {
            throw new IllegalArgumentException(
                    "OID config ID không được để trống"
            );
        }

        if (config.getDeviceId() == null) {
            throw new IllegalArgumentException(
                    "Device ID không được để trống"
            );
        }

        return config;
    }

    // =========================================================
    // EXTRACT ID
    // =========================================================

    /**
     * Hỗ trợ các payload:
     * {"oidConfigId": 12, "deviceId": 5}
     * {"id": 12}
     * hoặc truyền trực tiếp ID.
     */
    private Long extractId(Object data) {
        Object rawId = data;

        if (data instanceof Map<?, ?> map) {
            if (map.containsKey("oidConfigId")) {
                rawId = map.get("oidConfigId");
            } else {
                rawId = map.get("id");
            }
        }

        if (rawId instanceof Number number) {
            return number.longValue();
        }

        if (rawId instanceof String value) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "OID config ID không hợp lệ: " + value,
                        exception
                );
            }
        }

        throw new IllegalArgumentException(
                "Không thể đọc OID config ID từ payload"
        );
    }

    // =========================================================
    // CACHE MANAGEMENT
    // =========================================================

    private void putOidConfigToCache(OidConfigEventDto config) {
        if (config == null || config.getId() == null) {
            throw new IllegalArgumentException(
                    "OID config DTO hoặc ID không được null"
            );
        }

        oidConfigCache.updateAndGet(current -> {
            Map<Long, OidConfigEventDto> updated =
                    new HashMap<>(current);

            updated.put(config.getId(), config);

            return Map.copyOf(updated);
        });
    }

    // =========================================================
    // LOG OID CONFIG CACHE IN RAM
    // =========================================================

    /**
     * In toàn bộ OID config đang lưu trong RAM mỗi phút
     * để kiểm tra dữ liệu cache.
     */
    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Ho_Chi_Minh"
    )
    public void logOidConfigCache() {
        Map<Long, OidConfigEventDto> configs = oidConfigCache.get();

        log.info("========== OID CONFIG CACHE IN RAM ==========");
        log.info("Tổng số OID config trong RAM: {}", configs.size());

        if (configs.isEmpty()) {
            log.warn("OID config cache đang trống");
        } else {
            configs.values().stream()
                    .sorted(Comparator.comparing(OidConfigEventDto::getId))
                    .forEach(config -> log.info(
                            "OID Config RAM: id={}, deviceId={}, oid={}, metricName={}, dataType={}, multiplier={}, isActive={}",
                            config.getId(),
                            config.getDeviceId(),
                            config.getOid(),
                            config.getMetricName(),
                            config.getDataType(),
                            config.getMultiplier(),
                            config.getIsActive()
                    ));
        }

        log.info("==============================================");
    }
}

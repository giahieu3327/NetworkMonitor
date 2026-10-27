
package com.network_monitor.monitor_service.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.monitor_service.model.dto.kafka.credential.DeviceCredentialEventDto;
import com.network_monitor.monitor_service.service.DeviceCredentialService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
public class DeviceCredentialServiceImpl implements DeviceCredentialService {

    private final ObjectMapper objectMapper;

    /**
     * Cache credential hoàn toàn trong RAM.
     *
     * Key: credential ID (Long).
     * Value: DeviceCredentialEventDto.
     */
    private final AtomicReference<Map<Long, DeviceCredentialEventDto>>
            credentialCache = new AtomicReference<>(Map.of());

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public void createCredential(Object data) {
        DeviceCredentialEventDto credential = convertToCredentialDto(data);

        putCredentialToCache(credential);

        log.info(
                "Đã thêm DeviceCredential vào RAM: credentialId={}, deviceId={}",
                credential.getId(),
                credential.getDeviceId()
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    public void updateCredential(Object data) {
        DeviceCredentialEventDto credential = convertToCredentialDto(data);

        putCredentialToCache(credential);

        log.info(
                "Đã cập nhật DeviceCredential trong RAM: credentialId={}, deviceId={}",
                credential.getId(),
                credential.getDeviceId()
        );
    }

    // =========================================================
    // DELETE
    // =========================================================

    @Override
    public void deleteCredential(Object data) {
        Long credentialId = extractId(data);

        credentialCache.updateAndGet(current -> {
            if (!current.containsKey(credentialId)) {
                return current;
            }

            Map<Long, DeviceCredentialEventDto> updated =
                    new HashMap<>(current);

            updated.remove(credentialId);

            return Map.copyOf(updated);
        });

        log.info(
                "Đã xử lý xóa DeviceCredential khỏi RAM: credentialId={}",
                credentialId
        );
    }

    // =========================================================
    // QUERY FROM RAM
    // =========================================================

    @Override
    public Optional<DeviceCredentialEventDto> getCredentialById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(credentialCache.get().get(id));
    }

    @Override
    public List<DeviceCredentialEventDto> getCredentialsByDeviceId(
            Long deviceId
    ) {
        if (deviceId == null) {
            return List.of();
        }

        List<DeviceCredentialEventDto> result = new ArrayList<>();

        for (DeviceCredentialEventDto credential
                : credentialCache.get().values()) {

            if (deviceId.equals(credential.getDeviceId())) {
                result.add(credential);
            }
        }

        result.sort(Comparator.comparing(
                DeviceCredentialEventDto::getId
        ));

        return List.copyOf(result);
    }

    @Override
    public List<DeviceCredentialEventDto> getAllCredentials() {
        List<DeviceCredentialEventDto> credentials =
                new ArrayList<>(credentialCache.get().values());

        credentials.sort(Comparator.comparing(
                DeviceCredentialEventDto::getId
        ));

        return credentials;
    }

    // =========================================================
    // CONVERT PAYLOAD TO DTO
    // =========================================================

    private DeviceCredentialEventDto convertToCredentialDto(Object data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "DeviceCredential payload không được null"
            );
        }

        Map<String, Object> payload = objectMapper.convertValue(
                data,
                new TypeReference<Map<String, Object>>() {}
        );

        // Hỗ trợ payload sử dụng credentialId thay cho id.
        if (!payload.containsKey("id")
                && payload.containsKey("credentialId")) {
            payload.put("id", payload.get("credentialId"));
        }

        DeviceCredentialEventDto credential = objectMapper.convertValue(
                payload,
                DeviceCredentialEventDto.class
        );

        if (credential.getId() == null) {
            throw new IllegalArgumentException(
                    "Credential ID không được để trống"
            );
        }

        if (credential.getDeviceId() == null) {
            throw new IllegalArgumentException(
                    "Device ID không được để trống"
            );
        }

        if (credential.getProtocolType() == null
                || credential.getProtocolType().isBlank()) {
            throw new IllegalArgumentException(
                    "protocolType không được để trống"
            );
        }

        // Giá trị mặc định khi payload không cung cấp.
        if (credential.getPort() == null) {
            credential.setPort(161);
        }

        if (credential.getIsActive() == null) {
            credential.setIsActive(false);
        }

        LocalDateTime now = LocalDateTime.now();

        if (credential.getCreatedAt() == null) {
            credential.setCreatedAt(now);
        }

        if (credential.getUpdatedAt() == null) {
            credential.setUpdatedAt(now);
        }

        return credential;
    }

    // =========================================================
    // EXTRACT ID
    // =========================================================

    /**
     * Payload DELETE hỗ trợ:
     * { "credentialId": 12, "deviceId": 5 }
     * hoặc:
     * { "id": 12 }
     * hoặc truyền trực tiếp ID.
     */
    private Long extractId(Object data) {
        Object rawId = data;

        if (data instanceof Map<?, ?> map) {
            if (map.containsKey("credentialId")) {
                rawId = map.get("credentialId");
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
                        "Credential ID không hợp lệ: " + value,
                        exception
                );
            }
        }

        throw new IllegalArgumentException(
                "Không thể đọc Credential ID từ payload"
        );
    }

    // =========================================================
    // CACHE MANAGEMENT
    // =========================================================

    private void putCredentialToCache(
            DeviceCredentialEventDto credential
    ) {
        if (credential == null || credential.getId() == null) {
            throw new IllegalArgumentException(
                    "Credential DTO hoặc credential ID không được null"
            );
        }

        credentialCache.updateAndGet(current -> {
            Map<Long, DeviceCredentialEventDto> updated =
                    new HashMap<>(current);

            updated.put(credential.getId(), credential);

            return Map.copyOf(updated);
        });
    }

    // =========================================================
    // LOG CREDENTIAL CACHE IN RAM
    // =========================================================

    /**
     * In toàn bộ DeviceCredential đang lưu trong RAM mỗi phút
     * để kiểm tra dữ liệu cache.
     */
    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Ho_Chi_Minh"
    )
    public void logCredentialCache() {
        Map<Long, DeviceCredentialEventDto> credentials =
                credentialCache.get();

        log.info("========== DEVICE CREDENTIAL CACHE IN RAM ==========");
        log.info("Tổng số Credential trong RAM: {}", credentials.size());

        if (credentials.isEmpty()) {
            log.warn("Credential cache đang trống");
        } else {
            credentials.values().stream()
                    .sorted(Comparator.comparing(
                            DeviceCredentialEventDto::getId
                    ))
                    .forEach(credential -> log.info(
                            "Credential RAM: id={}, deviceId={}, credentialName={}, credentialType={}, protocolType={}, port={}, isActive={}, createdAt={}, updatedAt={}",
                            credential.getId(),
                            credential.getDeviceId(),
                            credential.getCredentialName(),
                            credential.getCredentialType(),
                            credential.getProtocolType(),
                            credential.getPort(),
                            credential.getIsActive(),
                            credential.getCreatedAt(),
                            credential.getUpdatedAt()
                    ));
        }

        log.info("====================================================");
    }
}

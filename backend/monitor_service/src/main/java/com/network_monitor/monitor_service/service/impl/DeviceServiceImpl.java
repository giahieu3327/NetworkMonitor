
package com.network_monitor.monitor_service.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.network_monitor.monitor_service.health.Health;
import com.network_monitor.monitor_service.model.dto.kafka.device.DeviceEventDto;
import com.network_monitor.monitor_service.model.dto.kafka.device.DeviceStatusEventDto;
import com.network_monitor.monitor_service.service.DeviceService;
import com.network_monitor.monitor_service.service.KafkaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
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
public class DeviceServiceImpl implements DeviceService {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String EVENT_UPDATE_STATUS = "UPDATE_STATUS";

    private final ObjectMapper objectMapper;
    private final Health health;
    private final KafkaService kafkaService;

    @Value("${monitoring.kafka.topics.device-status:portal.monitor.device}")
    private String deviceStatusTopic;

    /**
     * Cache thiết bị hoàn toàn trong RAM.
     * Key: deviceId (Long).
     * Value: DeviceEventDto.
     */
    private final AtomicReference<Map<Long, DeviceEventDto>> deviceCache =
            new AtomicReference<>(Map.of());

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public void createDevice(Object data) {
        DeviceEventDto device = convertToDeviceDto(data);

        updateDeviceCache(device);

        updateStatus();

        log.info(
                "Đã thêm Device vào RAM: deviceId={}, ip={}",
                device.getDeviceId(),
                device.getIpAddress()
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    public void updateDevice(Object data) {
        DeviceEventDto device = convertToDeviceDto(data);

        updateDeviceCache(device);

        updateStatus();

        log.info(
                "Đã cập nhật Device trong RAM: deviceId={}, ip={}",
                device.getDeviceId(),
                device.getIpAddress()
        );
    }

    // =========================================================
    // DELETE
    // =========================================================

    @Override
    public void deleteDevice(Object data) {
        Long id = extractId(data);

        deviceCache.updateAndGet(current -> {
            if (!current.containsKey(id)) {
                return current;
            }

            Map<Long, DeviceEventDto> updated = new HashMap<>(current);
            updated.remove(id);

            return Map.copyOf(updated);
        });

        log.info("Đã xóa Device khỏi RAM: deviceId={}", id);
    }

    // =========================================================
    // QUERY FROM RAM
    // =========================================================

    @Override
    public List<DeviceEventDto> getAllDevices() {
        List<DeviceEventDto> devices =
                new ArrayList<>(deviceCache.get().values());

        devices.sort(
                Comparator.comparing(DeviceEventDto::getDeviceId)
        );

        return devices;
    }

    @Override
    public Optional<DeviceEventDto> getDeviceById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(deviceCache.get().get(id));
    }

    // =========================================================
    // AUTOMATIC STATUS CHECK
    // =========================================================

    /**
     * Tự động chạy vào giây thứ 0 của mỗi phút,
     * theo múi giờ Việt Nam.
     */
    @Override
    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Ho_Chi_Minh"
    )
    public void updateStatus() {
        List<DeviceEventDto> devices = getAllDevices();

        if (devices.isEmpty()) {
            log.debug("Không có Device trong RAM để kiểm tra");
            return;
        }

        log.info(
                "Bắt đầu kiểm tra trạng thái {} thiết bị",
                devices.size()
        );

        for (DeviceEventDto device : devices) {
            processDeviceStatus(device);
        }

        log.info("Hoàn tất kiểm tra trạng thái thiết bị");
    }

    private void processDeviceStatus(DeviceEventDto device) {
        if (!isValidDevice(device)) {
            log.warn(
                    "Bỏ qua Device không hợp lệ: deviceId={}",
                    device == null ? null : device.getDeviceId()
            );
            return;
        }

        try {
            String newStatus = health.checkPing(device.getIpAddress())
                    ? STATUS_UP
                    : STATUS_DOWN;

            if (!hasStatusChanged(device.getStatus(), newStatus)) {
                log.debug(
                        "Status không thay đổi: deviceId={}, status={}",
                        device.getDeviceId(),
                        newStatus
                );
                return;
            }

            updateDeviceStatus(device, newStatus);

        } catch (Exception exception) {
            log.error(
                    "Lỗi kiểm tra trạng thái Device: deviceId={}, ip={}",
                    device.getDeviceId(),
                    device.getIpAddress(),
                    exception
            );
        }
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private boolean isValidDevice(DeviceEventDto device) {
        return device != null
                && device.getDeviceId() != null
                && device.getIpAddress() != null
                && !device.getIpAddress().isBlank();
    }

    // =========================================================
    // STATUS COMPARISON
    // =========================================================

    private boolean hasStatusChanged(
            String currentStatus,
            String newStatus
    ) {
        return currentStatus == null
                || !newStatus.equalsIgnoreCase(currentStatus);
    }

    // =========================================================
    // UPDATE STATUS IN RAM
    // =========================================================

    /**
     * Chỉ cập nhật RAM và gửi Kafka khi trạng thái thay đổi.
     */
    private void updateDeviceStatus(
            DeviceEventDto device,
            String newStatus
    ) {
        log.info(
                "Device status thay đổi: deviceId={}, ip={}, {} -> {}",
                device.getDeviceId(),
                device.getIpAddress(),
                device.getStatus(),
                newStatus
        );

        DeviceEventDto updatedDevice = copyWithStatus(device, newStatus);

        // 1. Cập nhật RAM.
        updateDeviceCache(updatedDevice);

        // 2. Gửi sự kiện sang Portal.
        publishDeviceStatus(updatedDevice);
    }

    private DeviceEventDto copyWithStatus(
            DeviceEventDto device,
            String newStatus
    ) {
        DeviceEventDto copy = objectMapper.convertValue(
                device,
                DeviceEventDto.class
        );

        copy.setStatus(newStatus);

        return copy;
    }

    // =========================================================
    // KAFKA
    // =========================================================

    private void publishDeviceStatus(DeviceEventDto device) {
        DeviceStatusEventDto event = new DeviceStatusEventDto();

        event.setDeviceId(device.getDeviceId());
        event.setStatus(device.getStatus());

        kafkaService.sendToPortal(
                deviceStatusTopic,
                EVENT_UPDATE_STATUS,
                String.valueOf(device.getDeviceId()),
                event
        ).whenComplete((sent, exception) -> {
            if (exception != null) {
                log.error(
                        "Lỗi gửi UPDATE_STATUS sang Portal: deviceId={}",
                        device.getDeviceId(),
                        exception
                );
                return;
            }

            if (Boolean.TRUE.equals(sent)) {
                log.info(
                        "Gửi UPDATE_STATUS thành công: deviceId={}, status={}",
                        device.getDeviceId(),
                        device.getStatus()
                );
            } else {
                log.error(
                        "Gửi UPDATE_STATUS thất bại: deviceId={}, status={}",
                        device.getDeviceId(),
                        device.getStatus()
                );
            }
        });
    }

    // =========================================================
    // CONVERT PAYLOAD TO DTO
    // =========================================================

    private DeviceEventDto convertToDeviceDto(Object data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Device payload không được null"
            );
        }

        Map<String, Object> payload = objectMapper.convertValue(
                data,
                new TypeReference<Map<String, Object>>() {}
        );

        // Hỗ trợ payload sử dụng "id" thay cho "deviceId".
        if (!payload.containsKey("deviceId")
                && payload.containsKey("id")) {
            payload.put("deviceId", payload.get("id"));
        }

        DeviceEventDto device = objectMapper.convertValue(
                payload,
                DeviceEventDto.class
        );

        if (device.getDeviceId() == null) {
            throw new IllegalArgumentException(
                    "Device ID không được để trống"
            );
        }

        return device;
    }

    private Long extractId(Object data) {
        Object rawId = data;

        if (data instanceof Map<?, ?> map) {
            rawId = map.containsKey("deviceId")
                    ? map.get("deviceId")
                    : map.get("id");
        }

        if (rawId instanceof Number number) {
            return number.longValue();
        }

        if (rawId instanceof String value) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Device ID không hợp lệ: " + value,
                        exception
                );
            }
        }

        throw new IllegalArgumentException(
                "Không thể đọc Device ID từ payload"
        );
    }

    // =========================================================
    // CACHE MANAGEMENT
    // =========================================================

    private void updateDeviceCache(DeviceEventDto device) {
        if (device == null || device.getDeviceId() == null) {
            throw new IllegalArgumentException(
                    "Device DTO hoặc deviceId không được null"
            );
        }

        deviceCache.updateAndGet(current -> {
            Map<Long, DeviceEventDto> updated = new HashMap<>(current);

            updated.put(device.getDeviceId(), device);

            return Map.copyOf(updated);
        });
    }

    // =========================================================
    // LOG DEVICE CACHE IN RAM
    // =========================================================

    /**
     * In toàn bộ Device đang lưu trong RAM mỗi phút
     * để kiểm tra dữ liệu cache.
     */
    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Ho_Chi_Minh"
    )
    public void logDeviceCache() {
        Map<Long, DeviceEventDto> devices = deviceCache.get();

        log.info("========== DEVICE CACHE IN RAM ==========");
        log.info("Tổng số Device trong RAM: {}", devices.size());

        if (devices.isEmpty()) {
            log.warn("Device cache đang trống");
        } else {
            devices.values().stream()
                    .sorted(Comparator.comparing(DeviceEventDto::getDeviceId))
                    .forEach(device -> log.info(
                            "Device RAM: deviceId={}, deviceName={}, ipAddress={}, deviceType={}, status={}",
                            device.getDeviceId(),
                            device.getDeviceName(),
                            device.getIpAddress(),
                            device.getDeviceType(),
                            device.getStatus()
                    ));
        }

        log.info("==========================================");
    }
}

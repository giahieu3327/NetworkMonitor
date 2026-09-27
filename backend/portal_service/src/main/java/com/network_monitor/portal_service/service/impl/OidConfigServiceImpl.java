package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.OidConfigRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.OidConfigResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.DeviceInterface;
import com.network_monitor.portal_service.model.entity.OidConfig;
import com.network_monitor.portal_service.model.enums.OidDataType;
import com.network_monitor.portal_service.repository.DeviceInterfaceRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.OidConfigRepository;
import com.network_monitor.portal_service.service.OidConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OidConfigServiceImpl implements OidConfigService {

    private final OidConfigRepository oidConfigRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceInterfaceRepository interfaceRepository;

    @Override
    @Transactional
    public ApiResponse<Void> createOidConfigs(List<OidConfigRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                return ApiResponse.error("Thêm cấu hình OID thất bại", "Danh sách OID không được để trống");
            }

            List<OidConfig> configsToSave = new ArrayList<>();
            for (OidConfigRequest req : requests) {
                OidConfig.OidConfigBuilder builder = OidConfig.builder()
                        .metricScope(req.getMetricScope())
                        .metricType(req.getMetricType())
                        .oidPattern(req.getOidPattern())
                        .dataType(req.getDataType() != null ? req.getDataType() : OidDataType.INTEGER)
                        .multiplier(req.getMultiplier() != null ? req.getMultiplier() : 1.0)
                        .deviceType(req.getDeviceType())
                        .description(req.getDescription())
                        .isActive(req.getIsActive() != null ? req.getIsActive() : true);

                if (req.getDeviceId() != null) {
                    Device device = deviceRepository.findById(req.getDeviceId())
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị với ID: " + req.getDeviceId()));
                    builder.device(device);
                }

                if (req.getInterfaceId() != null) {
                    DeviceInterface deviceInterface = interfaceRepository.findById(req.getInterfaceId())
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy cổng giao tiếp với ID: " + req.getInterfaceId()));
                    builder.deviceInterface(deviceInterface);
                }

                configsToSave.add(builder.build());
            }

            oidConfigRepository.saveAll(configsToSave);
            return ApiResponse.success("Thêm " + configsToSave.size() + " cấu hình OID thành công");
        } catch (Exception ex) {
            return ApiResponse.error("Thêm cấu hình OID thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> updateOidConfig(Long id, OidConfigRequest request) {
        try {
            OidConfig config = oidConfigRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cấu hình OID với ID: " + id));

            config.setMetricScope(request.getMetricScope());
            config.setMetricType(request.getMetricType());
            config.setOidPattern(request.getOidPattern());
            if (request.getDataType() != null) config.setDataType(request.getDataType());
            if (request.getMultiplier() != null) config.setMultiplier(request.getMultiplier());
            config.setDeviceType(request.getDeviceType());
            config.setDescription(request.getDescription());
            if (request.getIsActive() != null) config.setIsActive(request.getIsActive());

            if (request.getDeviceId() != null) {
                Device device = deviceRepository.findById(request.getDeviceId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị với ID: " + request.getDeviceId()));
                config.setDevice(device);
            } else {
                config.setDevice(null);
            }

            if (request.getInterfaceId() != null) {
                DeviceInterface deviceInterface = interfaceRepository.findById(request.getInterfaceId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy cổng giao tiếp với ID: " + request.getInterfaceId()));
                config.setDeviceInterface(deviceInterface);
            } else {
                config.setDeviceInterface(null);
            }

            oidConfigRepository.save(config);
            return ApiResponse.success("Cập nhật cấu hình OID thành công");
        } catch (Exception ex) {
            return ApiResponse.error("Cập nhật cấu hình OID thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteOidConfigs(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("Xóa cấu hình OID thất bại", "Danh sách ID không được để trống");
            }

            List<OidConfig> existingConfigs = oidConfigRepository.findAllById(ids);
            if (existingConfigs.isEmpty()) {
                return ApiResponse.error("Xóa cấu hình OID thất bại", "Không tìm thấy cấu hình OID nào phù hợp");
            }

            oidConfigRepository.deleteAll(existingConfigs);
            return ApiResponse.success("Xóa thành công " + existingConfigs.size() + " cấu hình OID");
        } catch (Exception ex) {
            return ApiResponse.error("Xóa cấu hình OID thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OidConfigResponse> getAllOidConfigs() {
        return oidConfigRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OidConfigResponse> getOidConfigsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return oidConfigRepository.findAllById(ids).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OidConfigResponse> getOidConfigsByDeviceId(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị với ID: " + deviceId));

        List<OidConfig> configs = oidConfigRepository.findActiveOidsForDevice(device.getId(), device.getDeviceType().name());
        return configs.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private OidConfigResponse mapToResponse(OidConfig config) {
        return OidConfigResponse.builder()
                .id(config.getId())
                .metricScope(config.getMetricScope())
                .metricType(config.getMetricType())
                .oidPattern(config.getOidPattern())
                .dataType(config.getDataType())
                .multiplier(config.getMultiplier())
                .deviceType(config.getDeviceType())
                .deviceId(config.getDevice() != null ? config.getDevice().getId() : null)
                .interfaceId(config.getDeviceInterface() != null ? config.getDeviceInterface().getId() : null)
                .isActive(config.getIsActive())
                .description(config.getDescription())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
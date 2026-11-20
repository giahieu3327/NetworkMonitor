
package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.DeviceInterfaceMapper;

import com.network_monitor.portal_service.model.dto.request.DeviceInterfaceRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceInterfaceResponse;

import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.DeviceInterface;

import com.network_monitor.portal_service.repository.DeviceInterfaceRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;

import com.network_monitor.portal_service.service.DeviceInterfaceService;
import com.network_monitor.portal_service.service.SnmpService;

import com.network_monitor.portal_service.util.SearchRequestUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceInterfaceServiceImpl
        implements DeviceInterfaceService {

    private final DeviceInterfaceRepository interfaceRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceInterfaceMapper interfaceMapper;
    private final SnmpService snmpService;

    // =====================================================
    // CREATE
    // =====================================================

    @Override
    @Transactional
    public ApiResponse<DeviceInterfaceResponse> addInterface(
            DeviceInterfaceRequest request) {

        try {

            if (request == null ||
                    request.getDeviceId() == null ||
                    request.getInterfaceIndex() == null) {

                return ApiResponse.error(
                        "Device ID và interface index không được để trống"
                );
            }

            if (request.getInterfaceIndex() <= 0) {

                return ApiResponse.error(
                        "Interface index phải lớn hơn 0"
                );
            }

            Device device = validateDevice(request.getDeviceId());

            DeviceInterface.DeviceInterfaceId id =
                    new DeviceInterface.DeviceInterfaceId(
                            request.getDeviceId(),
                            request.getInterfaceIndex()
                    );

            if (interfaceRepository.existsById(id)) {

                return ApiResponse.error(
                        "Interface đã tồn tại"
                );
            }

            DeviceInterface deviceInterface =
                    DeviceInterface.builder()
                            .deviceId(request.getDeviceId())
                            .interfaceIndex(request.getInterfaceIndex())
                            .device(device)
                            .build();

            applyRequest(deviceInterface, request);

            deviceInterface.setUpdatedAt(
                    OffsetDateTime.now()
            );

            DeviceInterface saved =
                    interfaceRepository.saveAndFlush(deviceInterface);

            return ApiResponse.success(
                    "Thêm interface thành công",
                    toResponse(saved)
            );

        } catch (Exception e) {

            log.error("Add interface failed", e);

            return ApiResponse.error(
                    getErrorMessage(e)
            );
        }
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Override
    @Transactional
    public ApiResponse<Void> updateInterface(
            Long deviceId,
            Integer interfaceIndex,
            DeviceInterfaceRequest request) {

        try {

            if (deviceId == null || interfaceIndex == null) {

                return ApiResponse.error(
                        "Device ID và interface index không được để trống"
                );
            }

            if (request == null) {

                return ApiResponse.error(
                        "Thông tin cập nhật không được để trống"
                );
            }

            DeviceInterface.DeviceInterfaceId id =
                    new DeviceInterface.DeviceInterfaceId(
                            deviceId,
                            interfaceIndex
                    );

            DeviceInterface deviceInterface =
                    interfaceRepository.findById(id)
                            .orElse(null);

            if (deviceInterface == null) {

                return ApiResponse.error(
                        "Không tìm thấy interface"
                );
            }

            // Cập nhật thông tin interface.
            applyRequest(deviceInterface, request);

            deviceInterface.setUpdatedAt(
                    OffsetDateTime.now()
            );

            interfaceRepository.saveAndFlush(deviceInterface);

            return ApiResponse.success(
                    "Cập nhật interface thành công",
                    null
            );

        } catch (Exception e) {

            log.error(
                    "Update interface failed: deviceId={}, interfaceIndex={}",
                    deviceId,
                    interfaceIndex,
                    e
            );

            return ApiResponse.error(
                    getErrorMessage(e)
            );
        }
    }

    // =====================================================
    // DELETE
    // =====================================================

    @Override
    @Transactional
    public ApiResponse<Void> deleteInterface(
            Long deviceId,
            Integer interfaceIndex) {

        try {

            if (deviceId == null || interfaceIndex == null) {

                return ApiResponse.error(
                        "Device ID và interface index không được để trống"
                );
            }

            DeviceInterface.DeviceInterfaceId id =
                    new DeviceInterface.DeviceInterfaceId(
                            deviceId,
                            interfaceIndex
                    );

            DeviceInterface deviceInterface =
                    interfaceRepository.findById(id)
                            .orElse(null);

            if (deviceInterface == null) {

                return ApiResponse.error(
                        "Không tìm thấy interface"
                );
            }

            interfaceRepository.delete(deviceInterface);

            return ApiResponse.success(
                    "Xóa interface thành công",
                    null
            );

        } catch (Exception e) {

            log.error(
                    "Delete interface failed: deviceId={}, interfaceIndex={}",
                    deviceId,
                    interfaceIndex,
                    e
            );

            return ApiResponse.error(
                    getErrorMessage(e)
            );
        }
    }

    // =====================================================
    // READ - GET ALL
    // =====================================================

    @Override
    @Transactional
    public Page<DeviceInterfaceResponse> getInterfacesByDeviceId(
            Long deviceId,
            SearchRequest request) {

        validateDevice(deviceId);

        discoverAndSyncInterfaces(deviceId);

        SearchRequest search =
                request != null ? request : new SearchRequest();

        Pageable pageable = createPageable(search);

        String keyword =
                SearchRequestUtils.normalizeKeyword(search);

        String sortBy =
                SearchRequestUtils.normalizeSortBy(search, "updatedAt");

        String direction =
                SearchRequestUtils.normalizeDirection(search);

        List<DeviceInterface> interfaces =
                interfaceMapper.searchInterfacesByDeviceId(
                        deviceId,
                        keyword,
                        pageable.getOffset(),
                        pageable.getPageSize(),
                        sortBy,
                        direction
                );

        long total =
                interfaceMapper.countInterfacesByDeviceId(
                        deviceId,
                        keyword
                );

        List<DeviceInterfaceResponse> responses =
                interfaces.stream()
                        .map(this::toResponse)
                        .toList();

        return new PageImpl<>(
                responses,
                pageable,
                total
        );
    }

    // =====================================================
    // READ - GET DETAIL
    // =====================================================

    @Override
    @Transactional
    public ApiResponse<DeviceInterfaceResponse> getInterfaceById(
            Long deviceId,
            Integer interfaceIndex) {

        try {

            if (deviceId == null || interfaceIndex == null) {

                return ApiResponse.error(
                        "Device ID và interface index không được để trống"
                );
            }

            validateDevice(deviceId);

            discoverAndSyncInterfaces(deviceId);

            DeviceInterface.DeviceInterfaceId id =
                    new DeviceInterface.DeviceInterfaceId(
                            deviceId,
                            interfaceIndex
                    );

            DeviceInterface deviceInterface =
                    interfaceRepository.findById(id)
                            .orElse(null);

            if (deviceInterface == null) {

                return ApiResponse.error(
                        "Không tìm thấy interface"
                );
            }

            return ApiResponse.success(
                    "Lấy thông tin interface thành công",
                    toResponse(deviceInterface)
            );

        } catch (Exception e) {

            log.error("Get interface detail failed", e);

            return ApiResponse.error(
                    getErrorMessage(e)
            );
        }
    }

    // =====================================================
    // SNMP DISCOVERY AND CRUD SYNCHRONIZATION
    // =====================================================

    /**
     * SNMP chỉ chịu trách nhiệm lấy dữ liệu.
     *
     * Service này quyết định:
     * - Interface chưa tồn tại: CREATE.
     * - Interface đã tồn tại: UPDATE.
     * - Interface không còn tồn tại: DELETE.
     */
    private void discoverAndSyncInterfaces(Long deviceId) {

        Map<String, Object> snmpResult =
                snmpService.pollInterfaceScope(deviceId);

        if (snmpResult == null) {

            throw new IllegalStateException(
                    "Không thể lấy dữ liệu interface từ SNMP"
            );
        }

        Object object = snmpResult.get("interfaces");

        if (!(object instanceof Map<?, ?> interfaces)) {

            throw new IllegalStateException(
                    "Dữ liệu interface SNMP không hợp lệ"
            );
        }

        Set<Integer> discoveredIndexes = new HashSet<>();

        // CREATE hoặc UPDATE từng interface.
        for (Map.Entry<?, ?> entry : interfaces.entrySet()) {

            Integer interfaceIndex = toInteger(entry.getKey());

            if (interfaceIndex == null || interfaceIndex <= 0) {
                continue;
            }

            if (!(entry.getValue() instanceof Map<?, ?> data)) {
                continue;
            }

            DeviceInterfaceRequest request =
                    toRequest(deviceId, interfaceIndex, data);

            if (request.getInterfaceName() == null ||
                    request.getInterfaceName().isBlank()) {
                continue;
            }

            DeviceInterface.DeviceInterfaceId id =
                    new DeviceInterface.DeviceInterfaceId(
                            deviceId,
                            interfaceIndex
                    );

            if (interfaceRepository.existsById(id)) {

                // UPDATE
                updateInterface(
                        deviceId,
                        interfaceIndex,
                        request
                );

            } else {

                // CREATE
                addInterface(request);
            }

            discoveredIndexes.add(interfaceIndex);
        }

        // DELETE chỉ khi discovery hoàn chỉnh.
        boolean discoverySuccess =
                Boolean.TRUE.equals(
                        snmpResult.get("discoverySuccess")
                );

        if (!discoverySuccess) {

            log.debug(
                    "Skip deleting interfaces because discovery is incomplete: deviceId={}",
                    deviceId
            );

            return;
        }

        List<DeviceInterface> existingInterfaces =
                interfaceRepository.findAllByDeviceId(deviceId);

        for (DeviceInterface existing : existingInterfaces) {

            Integer index = existing.getInterfaceIndex();

            if (index != null &&
                    !discoveredIndexes.contains(index)) {

                // DELETE
                deleteInterface(deviceId, index);
            }
        }
    }

    // =====================================================
    // CONVERT SNMP DATA TO REQUEST
    // =====================================================

    private DeviceInterfaceRequest toRequest(
            Long deviceId,
            Integer interfaceIndex,
            Map<?, ?> data) {

        DeviceInterfaceRequest request =
                DeviceInterfaceRequest.builder()
                        .deviceId(deviceId)
                        .interfaceIndex(interfaceIndex)
                        .build();

        for (Map.Entry<?, ?> entry : data.entrySet()) {

            String metricType =
                    String.valueOf(entry.getKey())
                            .toLowerCase()
                            .replace(" ", "")
                            .replace("_", "")
                            .replace("-", "");

            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            switch (metricType) {
                case "interfacename":
                    request.setInterfaceName(String.valueOf(value));
                    break;

                case "interfacetype":
                    request.setInterfaceType(toInteger(value));
                    break;

                case "interfacemtu":
                    request.setMtu(toInteger(value));
                    break;

                case "interfacespeed":
                    Long speedMbps = toLong(value);
                    request.setSpeedBps(
                        speedMbps != null
                                    ? speedMbps * 1_000_000L
                                    : null);
                    break;

                case "interfaceadminstatus":
                    request.setAdminStatus(
                            toAdminStatus(value)
                    );
                    break;

                case "interfaceoperstatus":
                    request.setOperStatus(
                            toOperStatus(value)
                    );
                    break;

                case "interfacephysaddress":
                    request.setPhysAddress(
                            String.valueOf(value)
                    );
                    break;

                case "interfacealias":
                    request.setInterfaceAlias(
                            String.valueOf(value)
                    );
                    break;
                
                case "interfaceipaddress":
                    request.setIpAddress(String.valueOf(value));
                    break;

                case "interfacesubnetmask":
                    request.setSubnetMask(String.valueOf(value));
                    break;
                
                default:
                    log.debug(
                            "Unsupported interface metric: {}",
                            metricType
                    );
            }
        }

        return request;
    }

    private String toAdminStatus(Object value) {

        if (value == null) {
                return null;
        }

        String status = String.valueOf(value).trim().toUpperCase();

        return switch (status) {
                case "1", "UP" -> "UP";
                case "2", "DOWN" -> "DOWN";
                case "3", "TESTING" -> "TESTING";
                case "4", "UNKNOWN" -> "UNKNOWN";
                default -> "UNKNOWN";
        };
        }

        private String toOperStatus(Object value) {

        if (value == null) {
                return null;
        }

        String status = String.valueOf(value).trim().toUpperCase();

        return switch (status) {
                case "1", "UP" -> "UP";
                case "2", "DOWN" -> "DOWN";
                case "3", "TESTING" -> "TESTING";
                case "4", "UNKNOWN" -> "UNKNOWN";
                case "5", "DORMANT" -> "DORMANT";
                case "6", "NOT_PRESENT" -> "NOT_PRESENT";
                case "7", "LOWER_LAYER_DOWN" -> "LOWER_LAYER_DOWN";
                default -> "UNKNOWN";
        };
        }

    // =====================================================
    // APPLY REQUEST TO ENTITY
    // =====================================================

    private void applyRequest(
            DeviceInterface entity,
            DeviceInterfaceRequest request) {

        if (request.getInterfaceName() != null) {
            entity.setInterfaceName(
                    request.getInterfaceName()
            );
        }

        if (request.getInterfaceType() != null) {
            entity.setInterfaceType(
                    request.getInterfaceType()
            );
        }

        if (request.getMtu() != null) {
            entity.setMtu(
                    request.getMtu()
            );
        }

        if (request.getSpeedBps() != null) {
            entity.setSpeedBps(
                    request.getSpeedBps()
            );
        }

        if (request.getPhysAddress() != null) {
            entity.setPhysAddress(
                    request.getPhysAddress()
            );
        }

        if (request.getAdminStatus() != null) {
            entity.setAdminStatus(
                    request.getAdminStatus()
            );
        }

        if (request.getOperStatus() != null) {
            entity.setOperStatus(
                    request.getOperStatus()
            );
        }

        if (request.getInterfaceAlias() != null) {
            entity.setInterfaceAlias(
                    request.getInterfaceAlias()
            );
        }

        if(request.getIpAddress() != null) {
            entity.setIpAddress(
                    request.getIpAddress()
            );
        }

        if(request.getSubnetMask() != null) {
            entity.setSubnetMask(
                    request.getSubnetMask()
            );
        }
    }

    // =====================================================
    // VALIDATE DEVICE
    // =====================================================

    private Device validateDevice(Long deviceId) {

        if (deviceId == null) {

            throw new IllegalArgumentException(
                    "Device ID không được để trống"
            );
        }

        return deviceRepository.findById(deviceId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy thiết bị"
                        )
                );
    }

    // =====================================================
    // PAGINATION
    // =====================================================

    private Pageable createPageable(SearchRequest request) {

        int page =
                SearchRequestUtils.normalizePage(request);

        int size =
                SearchRequestUtils.normalizeSize(request);

        String sortBy =
                SearchRequestUtils.normalizeSortBy(
                        request,
                        "updatedAt"
                );

        String direction =
                SearchRequestUtils.normalizeDirection(request);

        return SearchRequestUtils.toPageable(
                page,
                size,
                sortBy,
                direction
        );
    }

    // =====================================================
    // CONVERT VALUE
    // =====================================================

    private Integer toInteger(Object value) {

        if (value == null) {
            return null;
        }

        try {

            return (int) Double.parseDouble(
                    String.valueOf(value)
            );

        } catch (NumberFormatException e) {

            log.warn("Cannot convert to Integer: {}", value);

            return null;
        }
    }

    private Long toLong(Object value) {

        if (value == null) {
            return null;
        }

        try {

            return (long) Double.parseDouble(
                    String.valueOf(value)
            );

        } catch (NumberFormatException e) {

            log.warn("Cannot convert to Long: {}", value);

            return null;
        }
    }

    // =====================================================
    // RESPONSE MAPPING
    // =====================================================

    private DeviceInterfaceResponse toResponse(
            DeviceInterface entity) {

        return DeviceInterfaceResponse.builder()
                .deviceId(entity.getDeviceId())
                .deviceName(
                        entity.getDevice() != null
                                ? entity.getDevice().getDeviceName()
                                : null
                )
                .interfaceIndex(entity.getInterfaceIndex())
                .interfaceName(entity.getInterfaceName())
                .interfaceType(entity.getInterfaceType())
                .mtu(entity.getMtu())
                .speedBps(entity.getSpeedBps())
                .physAddress(entity.getPhysAddress())
                .adminStatus(entity.getAdminStatus())
                .operStatus(entity.getOperStatus())
                .interfaceAlias(entity.getInterfaceAlias())
                .ipAddress(entity.getIpAddress())
                .subnetMask(entity.getSubnetMask())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // =====================================================
    // ERROR MESSAGE
    // =====================================================

    private String getErrorMessage(Throwable exception) {

        Throwable cause = exception;

        while (cause.getCause() != null &&
                cause.getCause() != cause) {

            cause = cause.getCause();
        }

        return cause.getMessage() != null
                ? cause.getMessage()
                : "Đã xảy ra lỗi không xác định";
    }
}

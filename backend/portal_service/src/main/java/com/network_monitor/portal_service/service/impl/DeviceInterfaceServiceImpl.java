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

import com.network_monitor.portal_service.util.SearchRequestUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceInterfaceServiceImpl
        implements DeviceInterfaceService {

    private final DeviceInterfaceRepository interfaceRepository;

    private final DeviceRepository deviceRepository;

    private final DeviceInterfaceMapper interfaceMapper;


    // ======================================================
    // CREATE
    // ======================================================

    @Override
    @Transactional
    public ApiResponse<DeviceInterfaceResponse> addInterface(
            DeviceInterfaceRequest request) {

        try {

            validateRequest(request);

            // Kiểm tra thiết bị tồn tại.
            Device device = deviceRepository.findById(
                    request.getDeviceId()
            ).orElseThrow(() ->
                    new IllegalArgumentException(
                            "Không tìm thấy thiết bị"
                    )
            );

            // Tạo khóa chính kép.
            DeviceInterface.DeviceInterfaceId id =
                    new DeviceInterface.DeviceInterfaceId(
                            request.getDeviceId(),
                            request.getInterfaceIndex()
                    );

            // Kiểm tra interface đã tồn tại chưa.
            if (interfaceRepository.existsById(id)) {

                return ApiResponse.error(
                        "Interface index đã tồn tại trên thiết bị này"
                );
            }

            // Tạo interface mới.
            DeviceInterface deviceInterface = DeviceInterface.builder()

                    .deviceId(request.getDeviceId())

                    .interfaceIndex(request.getInterfaceIndex())

                    .device(device)

                    .interfaceName(request.getInterfaceName())

                    .interfaceType(request.getInterfaceType())

                    .mtu(request.getMtu())

                    .speedBps(request.getSpeedBps())

                    .physAddress(request.getPhysAddress())

                    .adminStatus(request.getAdminStatus())

                    .operStatus(request.getOperStatus())

                    .interfaceAlias(request.getInterfaceAlias())

                    .isMonitor(
                            request.getIsMonitor() == null
                                    ? Boolean.FALSE
                                    : request.getIsMonitor()
                    )

                    .updatedAt(OffsetDateTime.now())

                    .build();

            DeviceInterface saved =
                    interfaceRepository.saveAndFlush(deviceInterface);

            return ApiResponse.success(
                    "Thêm interface thành công",
                    toResponse(saved)
            );

        } catch (Exception e) {

            log.error("Add interface failed", e);

            return ApiResponse.error(getErrorMessage(e));
        }
    }


    // ======================================================
    // UPDATE
    // ======================================================

    @Override
    @Transactional
    public ApiResponse<Void> updateInterface(
            Long deviceId,
            Integer interfaceIndex,
            DeviceInterfaceRequest request) {

        try {

            validateRequest(request);

            if (deviceId == null || interfaceIndex == null) {

                return ApiResponse.error(
                        "Device ID và interface index không được để trống"
                );
            }

            // Không cho phép thay đổi khóa chính kép.
            if (!deviceId.equals(request.getDeviceId())
                    || !interfaceIndex.equals(request.getInterfaceIndex())) {

                return ApiResponse.error(
                        "Không được thay đổi Device ID hoặc Interface Index"
                );
            }

            // Tạo khóa chính kép.
            DeviceInterface.DeviceInterfaceId id =
                    new DeviceInterface.DeviceInterfaceId(
                            deviceId,
                            interfaceIndex
                    );

            // Tìm interface cần cập nhật.
            DeviceInterface existing =
                    interfaceRepository.findById(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Không tìm thấy interface"
                                    )
                            );

            // Cập nhật thông tin interface.
            existing.setInterfaceName(request.getInterfaceName());

            existing.setInterfaceType(request.getInterfaceType());

            existing.setMtu(request.getMtu());

            existing.setSpeedBps(request.getSpeedBps());

            existing.setPhysAddress(request.getPhysAddress());

            existing.setAdminStatus(request.getAdminStatus());

            existing.setOperStatus(request.getOperStatus());

            existing.setInterfaceAlias(request.getInterfaceAlias());

            // Chỉ cập nhật isMonitor khi request có truyền giá trị.
            if (request.getIsMonitor() != null) {

                existing.setIsMonitor(request.getIsMonitor());
            }

            existing.setUpdatedAt(OffsetDateTime.now());

            interfaceRepository.saveAndFlush(existing);

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

            return ApiResponse.error(getErrorMessage(e));
        }
    }


    // ======================================================
    // DELETE
    // ======================================================

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

            // Tạo khóa chính kép.
            DeviceInterface.DeviceInterfaceId id =
                    new DeviceInterface.DeviceInterfaceId(
                            deviceId,
                            interfaceIndex
                    );

            // Tìm interface cần xóa.
            DeviceInterface deviceInterface =
                    interfaceRepository.findById(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Không tìm thấy interface"
                                    )
                            );

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

            return ApiResponse.error(getErrorMessage(e));
        }
    }


    // ======================================================
    // GET ALL INTERFACES BY DEVICE ID - PAGINATION
    // ======================================================

    @Override
    @Transactional(readOnly = true)
    public Page<DeviceInterfaceResponse> getInterfacesByDeviceId(
            Long deviceId,
            SearchRequest request) {

        // Kiểm tra deviceId.
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {

            throw new IllegalArgumentException(
                    "Không tìm thấy thiết bị"
            );
        }

        SearchRequest search = request != null
                ? request
                : new SearchRequest();

        // Chuẩn hóa pagination.
        int page = SearchRequestUtils.normalizePage(search);

        int size = SearchRequestUtils.normalizeSize(search);

        String keyword = SearchRequestUtils.normalizeKeyword(search);

        String sortBy = SearchRequestUtils.normalizeSortBy(
                search,
                "updatedAt"
        );

        String direction = SearchRequestUtils.normalizeDirection(search);

        Pageable pageable = SearchRequestUtils.toPageable(
                page,
                size,
                sortBy,
                direction
        );

        // Lấy dữ liệu bằng MyBatis.
        List<DeviceInterface> interfaces =
                interfaceMapper.searchInterfacesByDeviceId(
                        deviceId,
                        keyword,
                        pageable.getOffset(),
                        pageable.getPageSize(),
                        sortBy,
                        direction
                );

        // Đếm tổng số interface.
        long total = interfaceMapper.countInterfacesByDeviceId(
                deviceId,
                keyword
        );

        // Chuyển Entity sang Response DTO.
        List<DeviceInterfaceResponse> responses = interfaces.stream()
                .map(this::toResponse)
                .toList();

        return new PageImpl<>(
                responses,
                pageable,
                total
        );
    }


    // ======================================================
    // GET MONITORED INTERFACES BY DEVICE ID - PAGINATION
    // ======================================================

    @Override
    @Transactional(readOnly = true)
    public Page<DeviceInterfaceResponse> getMonitoredInterfacesByDeviceId(
            Long deviceId,
            SearchRequest request) {

        // Kiểm tra deviceId.
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {

            throw new IllegalArgumentException(
                    "Không tìm thấy thiết bị"
            );
        }

        SearchRequest search = request != null
                ? request
                : new SearchRequest();

        // Chuẩn hóa pagination.
        int page = SearchRequestUtils.normalizePage(search);

        int size = SearchRequestUtils.normalizeSize(search);

        String keyword = SearchRequestUtils.normalizeKeyword(search);

        String sortBy = SearchRequestUtils.normalizeSortBy(
                search,
                "updatedAt"
        );

        String direction = SearchRequestUtils.normalizeDirection(search);

        Pageable pageable = SearchRequestUtils.toPageable(
                page,
                size,
                sortBy,
                direction
        );

        // Lấy dữ liệu bằng MyBatis.
        List<DeviceInterface> interfaces =
                interfaceMapper.searchMonitoredInterfacesByDeviceId(
                        deviceId,
                        keyword,
                        pageable.getOffset(),
                        pageable.getPageSize(),
                        sortBy,
                        direction
                );

        // Đếm tổng số interface đang monitoring.
        long total =
                interfaceMapper.countMonitoredInterfacesByDeviceId(
                        deviceId,
                        keyword
                );

        // Chuyển Entity sang Response DTO.
        List<DeviceInterfaceResponse> responses = interfaces.stream()
                .map(this::toResponse)
                .toList();

        return new PageImpl<>(
                responses,
                pageable,
                total
        );
    }


    // ======================================================
    // GET INTERFACE DETAIL
    // ======================================================

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<DeviceInterfaceResponse> getInterfaceById(
            Long deviceId,
            Integer interfaceIndex) {

        try {

            if (deviceId == null || interfaceIndex == null) {

                return ApiResponse.error(
                        "Device ID và interface index không được để trống"
                );
            }

            // Tạo khóa chính kép.
            DeviceInterface.DeviceInterfaceId id =
                    new DeviceInterface.DeviceInterfaceId(
                            deviceId,
                            interfaceIndex
                    );

            return interfaceRepository.findById(id)

                    .map(deviceInterface ->
                            ApiResponse.success(
                                    "Lấy thông tin interface thành công",
                                    toResponse(deviceInterface)
                            )
                    )

                    .orElseGet(() ->
                            ApiResponse.error(
                                    "Không tìm thấy interface"
                            )
                    );

        } catch (Exception e) {

            log.error(
                    "Get interface detail failed: deviceId={}, interfaceIndex={}",
                    deviceId,
                    interfaceIndex,
                    e
            );

            return ApiResponse.error(getErrorMessage(e));
        }
    }


    // ======================================================
    // VALIDATION
    // ======================================================

    private void validateRequest(DeviceInterfaceRequest request) {

        if (request == null) {

            throw new IllegalArgumentException(
                    "Thông tin interface không được để trống"
            );
        }

        if (request.getDeviceId() == null) {

            throw new IllegalArgumentException(
                    "Device ID không được để trống"
            );
        }

        if (request.getInterfaceIndex() == null
                || request.getInterfaceIndex() <= 0) {

            throw new IllegalArgumentException(
                    "Interface index phải lớn hơn 0"
            );
        }

        if (request.getInterfaceName() == null
                || request.getInterfaceName().isBlank()) {

            throw new IllegalArgumentException(
                    "Interface name không được để trống"
            );
        }

        if (request.getInterfaceName().length() > 150) {

            throw new IllegalArgumentException(
                    "Interface name không được vượt quá 150 ký tự"
            );
        }

        if (request.getMtu() != null && request.getMtu() <= 0) {

            throw new IllegalArgumentException(
                    "MTU phải lớn hơn 0"
            );
        }

        if (request.getSpeedBps() != null
                && request.getSpeedBps() < 0) {

            throw new IllegalArgumentException(
                    "Speed BPS không được âm"
            );
        }

        if (request.getPhysAddress() != null
                && request.getPhysAddress().length() > 64) {

            throw new IllegalArgumentException(
                    "Physical address không được vượt quá 64 ký tự"
            );
        }

        if (request.getAdminStatus() != null
                && request.getAdminStatus().length() > 30) {

            throw new IllegalArgumentException(
                    "Admin status không được vượt quá 30 ký tự"
            );
        }

        if (request.getOperStatus() != null
                && request.getOperStatus().length() > 30) {

            throw new IllegalArgumentException(
                    "Oper status không được vượt quá 30 ký tự"
            );
        }
    }


    // ======================================================
    // RESPONSE MAPPING
    // ======================================================

    private DeviceInterfaceResponse toResponse(
            DeviceInterface deviceInterface) {

        return DeviceInterfaceResponse.builder()

                .deviceId(deviceInterface.getDeviceId())

                .deviceName(
                        deviceInterface.getDevice() != null
                                ? deviceInterface.getDevice().getDeviceName()
                                : null
                )

                .interfaceIndex(deviceInterface.getInterfaceIndex())

                .interfaceName(deviceInterface.getInterfaceName())

                .interfaceType(deviceInterface.getInterfaceType())

                .mtu(deviceInterface.getMtu())

                .speedBps(deviceInterface.getSpeedBps())

                .physAddress(deviceInterface.getPhysAddress())

                .adminStatus(deviceInterface.getAdminStatus())

                .operStatus(deviceInterface.getOperStatus())

                .interfaceAlias(deviceInterface.getInterfaceAlias())

                .isMonitor(deviceInterface.getIsMonitor())

                .updatedAt(deviceInterface.getUpdatedAt())

                .build();
    }


    // ======================================================
    // ERROR HANDLING
    // ======================================================

    private String getErrorMessage(Throwable exception) {

        Throwable cause = exception;

        while (cause.getCause() != null
                && cause.getCause() != cause) {

            cause = cause.getCause();
        }

        return cause.getMessage() != null
                ? cause.getMessage()
                : "Đã xảy ra lỗi không xác định";
    }
}
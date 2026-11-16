package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.ArpMacTableRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.ArpMacTableResponse;
import com.network_monitor.portal_service.model.entity.ArpMacTable;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.repository.ArpMacTableRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.ArpMacTableService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ArpMacTableServiceImpl implements ArpMacTableService {

    private final ArpMacTableRepository arpMacTableRepository;
    private final DeviceRepository deviceRepository;

    // ==================== CREATE ====================

    @Override
    public ApiResponse<ArpMacTableResponse> insert(
            ArpMacTableRequest request
    ) {
        try {
            validateRequest(request);

            // Kiểm tra thiết bị tồn tại.
            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            // Kiểm tra interface index.
            validateInterfaceIndex(request.getInterfaceIndex());

            // Kiểm tra IP đã tồn tại trên thiết bị chưa.
            if (arpMacTableRepository.existsByDevice_IdAndIpAddress(
                    request.getDeviceId(),
                    request.getIpAddress()
            )) {
                return ApiResponse.error(
                        "Địa chỉ IP đã tồn tại trên thiết bị này"
                );
            }

            ArpMacTable arpMacTable = ArpMacTable.builder()
                    .device(device)
                    .interfaceIndex(request.getInterfaceIndex())
                    .ipAddress(request.getIpAddress())
                    .macAddress(request.getMacAddress())
                    .updatedAt(OffsetDateTime.now())
                    .build();

            ArpMacTable saved =
                    arpMacTableRepository.saveAndFlush(arpMacTable);

            return ApiResponse.success(
                    "Thêm ARP record thành công",
                    toResponse(saved)
            );

        } catch (Exception e) {
            log.error(
                    "Add ARP record failed: deviceId={}, ipAddress={}",
                    request != null ? request.getDeviceId() : null,
                    request != null ? request.getIpAddress() : null,
                    e
            );

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // ==================== UPDATE ====================

    @Override
    public ApiResponse<ArpMacTableResponse> update(
            Long id,
            ArpMacTableRequest request
    ) {
        try {
            validateRequest(request);

            // Kiểm tra ARP record tồn tại.
            ArpMacTable existing =
                    arpMacTableRepository.findById(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Không tìm thấy ARP record"
                                    ));

            // Kiểm tra thiết bị tồn tại.
            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            validateInterfaceIndex(request.getInterfaceIndex());

            // Kiểm tra IP có bị trùng với bản ghi khác không.
            if (arpMacTableRepository
                    .existsByDevice_IdAndIpAddressAndIdNot(
                            request.getDeviceId(),
                            request.getIpAddress(),
                            id
                    )) {
                return ApiResponse.error(
                        "Địa chỉ IP đã tồn tại trên thiết bị này"
                );
            }

            existing.setDevice(device);
            existing.setInterfaceIndex(request.getInterfaceIndex());
            existing.setIpAddress(request.getIpAddress());
            existing.setMacAddress(request.getMacAddress());
            existing.setUpdatedAt(OffsetDateTime.now());

            ArpMacTable updated =
                    arpMacTableRepository.saveAndFlush(existing);

            return ApiResponse.success(
                    "Cập nhật ARP record thành công",
                    toResponse(updated)
            );

        } catch (Exception e) {
            log.error(
                    "Update ARP record failed: id={}",
                    id,
                    e
            );

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // ==================== DELETE ====================

    @Override
    public ApiResponse<Void> delete(Long id) {
        try {
            ArpMacTable existing =
                    arpMacTableRepository.findById(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Không tìm thấy ARP record"
                                    ));

            arpMacTableRepository.delete(existing);

            return ApiResponse.success(
                    "Xóa ARP record thành công",
                    null
            );

        } catch (Exception e) {
            log.error(
                    "Delete ARP record failed: id={}",
                    id,
                    e
            );

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // ==================== GET BY DEVICE ID ====================

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<ArpMacTableResponse>> getByDeviceId(
            Long deviceId
    ) {
        try {
            // Kiểm tra thiết bị tồn tại.
            if (!deviceRepository.existsById(deviceId)) {
                return ApiResponse.error(
                        "Không tìm thấy thiết bị"
                );
            }

            List<ArpMacTableResponse> responses =
                    arpMacTableRepository.findAllByDevice_Id(deviceId)
                            .stream()
                            .map(this::toResponse)
                            .toList();

            return ApiResponse.success(
                    "Lấy danh sách ARP record thành công",
                    responses
            );

        } catch (Exception e) {
            log.error(
                    "Get ARP records failed: deviceId={}",
                    deviceId,
                    e
            );

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // ==================== VALIDATION ====================

    private void validateRequest(ArpMacTableRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Thông tin ARP không được để trống"
            );
        }

        if (request.getDeviceId() == null) {
            throw new IllegalArgumentException(
                    "Device ID không được để trống"
            );
        }

        if (request.getIpAddress() == null
                || request.getIpAddress().isBlank()) {
            throw new IllegalArgumentException(
                    "IP address không được để trống"
            );
        }

        if (request.getMacAddress() == null
                || request.getMacAddress().isBlank()) {
            throw new IllegalArgumentException(
                    "MAC address không được để trống"
            );
        }
    }

    private void validateInterfaceIndex(Integer interfaceIndex) {
        if (interfaceIndex != null && interfaceIndex <= 0) {
            throw new IllegalArgumentException(
                    "Interface index phải lớn hơn 0"
            );
        }
    }

    // ==================== RESPONSE MAPPING ====================

    private ArpMacTableResponse toResponse(ArpMacTable arpMacTable) {
        return ArpMacTableResponse.builder()
                .id(arpMacTable.getId())
                .deviceId(arpMacTable.getDevice().getId())
                .deviceName(arpMacTable.getDevice().getDeviceName())
                .interfaceIndex(arpMacTable.getInterfaceIndex())
                .ipAddress(arpMacTable.getIpAddress())
                .macAddress(arpMacTable.getMacAddress())
                .updatedAt(arpMacTable.getUpdatedAt())
                .build();
    }

    // ==================== ERROR HANDLING ====================

    private String getErrorMessage(Throwable exception) {
        Throwable cause = exception;

        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        return cause.getMessage() != null
                ? cause.getMessage()
                : "Đã xảy ra lỗi không xác định";
    }
}
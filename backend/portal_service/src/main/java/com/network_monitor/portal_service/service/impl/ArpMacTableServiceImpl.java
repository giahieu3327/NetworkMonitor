
package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.ArpMacTableMapper;

import com.network_monitor.portal_service.model.dto.request.ArpMacTableRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.ArpMacTableResponse;

import com.network_monitor.portal_service.model.entity.ArpMacTable;
import com.network_monitor.portal_service.model.entity.Device;

import com.network_monitor.portal_service.repository.ArpMacTableRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;

import com.network_monitor.portal_service.service.ArpMacTableService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArpMacTableServiceImpl implements ArpMacTableService {

    private final ArpMacTableRepository arpMacTableRepository;
    private final DeviceRepository deviceRepository;
    private final ArpMacTableMapper arpMacTableMapper;
    private final SnmpService snmpService;

    // =====================================================
    // CREATE
    // =====================================================

    @Override
    @Transactional
    public ApiResponse<ArpMacTableResponse> insert(
            ArpMacTableRequest request) {

        try {

            if (request == null ||
                    request.getDeviceId() == null ||
                    request.getIpAddress() == null ||
                    request.getIpAddress().isBlank() ||
                    request.getMacAddress() == null ||
                    request.getMacAddress().isBlank()) {

                return ApiResponse.error(
                        "Device ID, IP address và MAC address không được để trống"
                );
            }

            Device device = validateDevice(request.getDeviceId());

            String ipAddress = request.getIpAddress().trim();

            if (arpMacTableRepository.existsByDevice_IdAndIpAddress(
                    request.getDeviceId(),
                    ipAddress)) {

                return ApiResponse.error(
                        "Địa chỉ IP đã tồn tại trên thiết bị này"
                );
            }

            ArpMacTable arpMacTable =
                    ArpMacTable.builder()
                            .device(device)
                            .ipAddress(ipAddress)
                            .macAddress(request.getMacAddress().trim())
                            .updatedAt(OffsetDateTime.now())
                            .build();

            ArpMacTable saved =
                    arpMacTableRepository.saveAndFlush(arpMacTable);

            return ApiResponse.success(
                    "Thêm ARP record thành công",
                    toResponse(saved)
            );

        } catch (Exception e) {

            log.error("Add ARP record failed", e);

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Override
    @Transactional
    public ApiResponse<ArpMacTableResponse> update(
            Long id,
            ArpMacTableRequest request) {

        try {

            if (id == null) {

                return ApiResponse.error(
                        "ARP record ID không được để trống"
                );
            }

            if (request == null ||
                    request.getDeviceId() == null ||
                    request.getIpAddress() == null ||
                    request.getIpAddress().isBlank() ||
                    request.getMacAddress() == null ||
                    request.getMacAddress().isBlank()) {

                return ApiResponse.error(
                        "Thông tin ARP không được để trống"
                );
            }

            ArpMacTable existing =
                    arpMacTableRepository.findById(id)
                            .orElse(null);

            if (existing == null) {

                return ApiResponse.error(
                        "Không tìm thấy ARP record"
                );
            }

            Device device = validateDevice(request.getDeviceId());

            String ipAddress = request.getIpAddress().trim();

            if (arpMacTableRepository
                    .existsByDevice_IdAndIpAddressAndIdNot(
                            request.getDeviceId(),
                            ipAddress,
                            id)) {

                return ApiResponse.error(
                        "Địa chỉ IP đã tồn tại trên thiết bị này"
                );
            }

            existing.setDevice(device);
            existing.setIpAddress(ipAddress);
            existing.setMacAddress(request.getMacAddress().trim());
            existing.setUpdatedAt(OffsetDateTime.now());

            ArpMacTable updated =
                    arpMacTableRepository.saveAndFlush(existing);

            return ApiResponse.success(
                    "Cập nhật ARP record thành công",
                    toResponse(updated)
            );

        } catch (Exception e) {

            log.error("Update ARP record failed: id={}", id, e);

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =====================================================
    // DELETE
    // =====================================================

    @Override
    @Transactional
    public ApiResponse<Void> delete(Long id) {

        try {

            if (id == null) {

                return ApiResponse.error(
                        "ARP record ID không được để trống"
                );
            }

            ArpMacTable existing =
                    arpMacTableRepository.findById(id)
                            .orElse(null);

            if (existing == null) {

                return ApiResponse.error(
                        "Không tìm thấy ARP record"
                );
            }

            arpMacTableRepository.delete(existing);

            return ApiResponse.success(
                    "Xóa ARP record thành công",
                    null
            );

        } catch (Exception e) {

            log.error("Delete ARP record failed: id={}", id, e);

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =====================================================
    // READ - GET ALL BY DEVICE ID
    // =====================================================

    @Override
    @Transactional
    public Page<ArpMacTableResponse> getByDeviceId(
            Long deviceId,
            SearchRequest request) {

        validateDevice(deviceId);

        // SNMP discovery và đồng bộ database.
        discoverAndSyncArpEntries(deviceId);

        SearchRequest search =
                request != null ? request : new SearchRequest();

        Pageable pageable = createPageable(search);

        String keyword =
                SearchRequestUtils.normalizeKeyword(search);

        String sortBy =
                SearchRequestUtils.normalizeSortBy(
                        search,
                        "updatedAt"
                );

        String direction =
                SearchRequestUtils.normalizeDirection(search);

        List<ArpMacTable> arpEntries =
                arpMacTableMapper.searchByDeviceId(
                        deviceId,
                        keyword,
                        pageable.getOffset(),
                        pageable.getPageSize(),
                        sortBy,
                        direction
                );

        long total =
                arpMacTableMapper.countByDeviceId(
                        deviceId,
                        keyword
                );

        List<ArpMacTableResponse> responses =
                arpEntries.stream()
                        .map(this::toResponse)
                        .toList();

        return new PageImpl<>(
                responses,
                pageable,
                total
        );
    }

    // =====================================================
    // SNMP DISCOVERY AND CRUD SYNCHRONIZATION
    // =====================================================

    private void discoverAndSyncArpEntries(Long deviceId) {

        Map<String, Object> snmpResult =
                snmpService.pollArpScope(deviceId);

        if (snmpResult == null) {

            throw new IllegalStateException(
                    "Không thể lấy dữ liệu ARP từ SNMP"
            );
        }

        Object object = snmpResult.get("arpEntries");

        if (!(object instanceof List<?> arpEntries)) {

            throw new IllegalStateException(
                    "Dữ liệu ARP SNMP không hợp lệ"
            );
        }

        List<ArpMacTable> existingEntries =
                arpMacTableRepository.findAllByDevice_Id(deviceId);

        Map<String, ArpMacTable> existingByIp = new HashMap<>();

        for (ArpMacTable entry : existingEntries) {

            existingByIp.put(
                    entry.getIpAddress(),
                    entry
            );
        }

        Set<String> discoveredIps = new HashSet<>();

        Device device = validateDevice(deviceId);

        // CREATE hoặc UPDATE.
        for (Object item : arpEntries) {

            if (!(item instanceof Map<?, ?> data)) {
                continue;
            }

            String ipAddress =
                    toStringValue(data.get("ipAddress"));

            String macAddress =
                    toStringValue(data.get("macAddress"));

            if (ipAddress == null || macAddress == null) {
                continue;
            }

            discoveredIps.add(ipAddress);

            ArpMacTable existing =
                    existingByIp.get(ipAddress);

            if (existing != null) {

                // UPDATE
                existing.setMacAddress(macAddress);
                existing.setUpdatedAt(OffsetDateTime.now());

                arpMacTableRepository.saveAndFlush(existing);

            } else {

                // CREATE
                ArpMacTable newEntry =
                        ArpMacTable.builder()
                                .device(device)
                                .ipAddress(ipAddress)
                                .macAddress(macAddress)
                                .updatedAt(OffsetDateTime.now())
                                .build();

                ArpMacTable saved =
                        arpMacTableRepository.saveAndFlush(newEntry);

                existingByIp.put(ipAddress, saved);
            }
        }

        // Chỉ DELETE khi SNMP discovery hoàn chỉnh.
        boolean discoverySuccess =
                Boolean.TRUE.equals(
                        snmpResult.get("discoverySuccess")
                );

        if (!discoverySuccess) {

            log.warn(
                    "Skip deleting ARP records because discovery is incomplete: deviceId={}",
                    deviceId
            );

            return;
        }

        int deletedCount = 0;

        for (Map.Entry<String, ArpMacTable> entry :
                existingByIp.entrySet()) {

            if (!discoveredIps.contains(entry.getKey())) {

                arpMacTableRepository.delete(entry.getValue());

                deletedCount++;
            }
        }

        log.info(
                "ARP synchronization completed: deviceId={}, discovered={}, deleted={}",
                deviceId,
                discoveredIps.size(),
                deletedCount
        );
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

    private String toStringValue(Object value) {

        if (value == null) {
            return null;
        }

        String result = String.valueOf(value).trim();

        return result.isEmpty() ? null : result;
    }

    // =====================================================
    // RESPONSE MAPPING
    // =====================================================

    private ArpMacTableResponse toResponse(
            ArpMacTable entity) {

        return ArpMacTableResponse.builder()
                .id(entity.getId())
                .deviceId(entity.getDevice() != null
                        ? entity.getDevice().getId()
                        : null)
                .deviceName(entity.getDevice() != null
                        ? entity.getDevice().getDeviceName()
                        : null)
                .ipAddress(entity.getIpAddress())
                .macAddress(entity.getMacAddress())
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

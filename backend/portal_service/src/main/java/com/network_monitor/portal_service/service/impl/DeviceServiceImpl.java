package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.DeviceRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.enums.DeviceStatus;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;

    @Override
    @Transactional
    public ApiResponse<Void> createDevices(List<DeviceRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                return ApiResponse.error("Thêm thiết bị thất bại", "Danh sách thiết bị không được để trống");
            }

            List<Device> devicesToSave = new ArrayList<>();
            for (DeviceRequest req : requests) {
                if (deviceRepository.findByIpAddress(req.getIpAddress()).isPresent()) {
                    return ApiResponse.error("Thêm thiết bị thất bại", "Địa chỉ IP " + req.getIpAddress() + " đã tồn tại trong hệ thống");
                }

                Device device = Device.builder()
                        .deviceName(req.getDeviceName())
                        .ipAddress(req.getIpAddress())
                        .deviceType(req.getDeviceType())
                        .model(req.getModel())
                        .firmwareVersion(req.getFirmwareVersion())
                        .status(req.getStatus() != null ? req.getStatus() : DeviceStatus.UP)
                        .build();

                devicesToSave.add(device);
            }

            deviceRepository.saveAll(devicesToSave);
            return ApiResponse.success("Thêm " + devicesToSave.size() + " thiết bị thành công");
        } catch (Exception ex) {
            return ApiResponse.error("Thêm thiết bị thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> updateDevice(Long id, DeviceRequest request) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị với ID: " + id));

            if (!device.getIpAddress().equals(request.getIpAddress()) &&
                deviceRepository.findByIpAddress(request.getIpAddress()).isPresent()) {
                return ApiResponse.error("Cập nhật thiết bị thất bại", "Địa chỉ IP " + request.getIpAddress() + " đã thuộc về thiết bị khác");
            }

            device.setDeviceName(request.getDeviceName());
            device.setIpAddress(request.getIpAddress());
            device.setDeviceType(request.getDeviceType());
            device.setModel(request.getModel());
            device.setFirmwareVersion(request.getFirmwareVersion());
            if (request.getStatus() != null) {
                device.setStatus(request.getStatus());
            }

            deviceRepository.save(device);
            return ApiResponse.success("Cập nhật thông tin thiết bị thành công");
        } catch (Exception ex) {
            return ApiResponse.error("Cập nhật thiết bị thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteDevices(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("Xóa thiết bị thất bại", "Danh sách ID cần xóa không được để trống");
            }

            List<Device> existingDevices = deviceRepository.findAllById(ids);
            if (existingDevices.isEmpty()) {
                return ApiResponse.error("Xóa thiết bị thất bại", "Không tìm thấy thiết bị nào phù hợp trong danh sách ID cung cấp");
            }

            deviceRepository.deleteAll(existingDevices);
            return ApiResponse.success("Xóa thành công " + existingDevices.size() + " thiết bị");
        } catch (Exception ex) {
            return ApiResponse.error("Xóa thiết bị thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevices() {
        return deviceRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevicesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return deviceRepository.findAllById(ids).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private DeviceResponse mapToResponse(Device device) {
        return DeviceResponse.builder()
                .id(device.getId())
                .deviceName(device.getDeviceName())
                .ipAddress(device.getIpAddress())
                .deviceType(device.getDeviceType())
                .model(device.getModel())
                .firmwareVersion(device.getFirmwareVersion())
                .status(device.getStatus())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }
}
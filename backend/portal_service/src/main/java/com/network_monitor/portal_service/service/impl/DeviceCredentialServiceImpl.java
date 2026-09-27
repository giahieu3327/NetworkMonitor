package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.DeviceCredentialRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceCredentialResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.DeviceCredential;
import com.network_monitor.portal_service.repository.DeviceCredentialRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.DeviceCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceCredentialServiceImpl implements DeviceCredentialService {

    private final DeviceCredentialRepository credentialRepository;
    private final DeviceRepository deviceRepository;

    @Override
    @Transactional
    public ApiResponse<Void> addCredentials(List<DeviceCredentialRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                return ApiResponse.error("Thêm cấu hình xác thực thất bại", "Danh sách cấu hình không được để trống");
            }

            List<DeviceCredential> credentialsToSave = new ArrayList<>();
            for (DeviceCredentialRequest req : requests) {
                Device device = deviceRepository.findById(req.getDeviceId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị với ID: " + req.getDeviceId()));

                if (Boolean.TRUE.equals(req.getIsPrimary())) {
                    unsetOtherPrimaryCredentials(req.getDeviceId(), req.getProtocolType());
                }

                DeviceCredential credential = DeviceCredential.builder()
                        .device(device)
                        .protocolType(req.getProtocolType())
                        .port(req.getPort() != null ? req.getPort() : 161)
                        .communityString(req.getCommunityString())
                        .isPrimary(req.getIsPrimary() != null ? req.getIsPrimary() : false)
                        .build();

                credentialsToSave.add(credential);
            }

            credentialRepository.saveAll(credentialsToSave);
            return ApiResponse.success("Thêm " + credentialsToSave.size() + " cấu hình xác thực thành công");
        } catch (Exception ex) {
            return ApiResponse.error("Thêm cấu hình xác thực thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> updateCredential(Long id, DeviceCredentialRequest request) {
        try {
            DeviceCredential credential = credentialRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cấu hình xác thực với ID: " + id));

            if (Boolean.TRUE.equals(request.getIsPrimary())) {
                unsetOtherPrimaryCredentials(credential.getDevice().getId(), request.getProtocolType());
            }

            credential.setProtocolType(request.getProtocolType());
            if (request.getPort() != null) credential.setPort(request.getPort());
            if (request.getCommunityString() != null) credential.setCommunityString(request.getCommunityString());
            if (request.getIsPrimary() != null) credential.setIsPrimary(request.getIsPrimary());

            credentialRepository.save(credential);
            return ApiResponse.success("Cập nhật cấu hình xác thực thành công");
        } catch (Exception ex) {
            return ApiResponse.error("Cập nhật cấu hình xác thực thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteCredentials(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("Xóa cấu hình xác thực thất bại", "Danh sách ID không được để trống");
            }

            List<DeviceCredential> existingCreds = credentialRepository.findAllById(ids);
            if (existingCreds.isEmpty()) {
                return ApiResponse.error("Xóa cấu hình xác thực thất bại", "Không tìm thấy cấu hình xác thực nào phù hợp");
            }

            credentialRepository.deleteAll(existingCreds);
            return ApiResponse.success("Xóa thành công " + existingCreds.size() + " cấu hình xác thực");
        } catch (Exception ex) {
            return ApiResponse.error("Xóa cấu hình xác thực thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceCredentialResponse> getAllCredentials() {
        return credentialRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceCredentialResponse> getCredentialsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return credentialRepository.findAllById(ids).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceCredentialResponse> getCredentialsByDeviceId(Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new RuntimeException("Không tìm thấy thiết bị với ID: " + deviceId);
        }
        return credentialRepository.findByDeviceId(deviceId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void unsetOtherPrimaryCredentials(Long deviceId, com.network_monitor.portal_service.model.enums.ProtocolType protocolType) {
        List<DeviceCredential> existingCreds = credentialRepository.findByDeviceId(deviceId);
        for (DeviceCredential cred : existingCreds) {
            if (cred.getProtocolType() == protocolType && Boolean.TRUE.equals(cred.getIsPrimary())) {
                cred.setIsPrimary(false);
                credentialRepository.save(cred);
            }
        }
    }

    private DeviceCredentialResponse mapToResponse(DeviceCredential credential) {
        return DeviceCredentialResponse.builder()
                .id(credential.getId())
                .deviceId(credential.getDevice().getId())
                .protocolType(credential.getProtocolType())
                .port(credential.getPort())
                .communityString(credential.getCommunityString())
                .isPrimary(credential.getIsPrimary())
                .createdAt(credential.getCreatedAt())
                .updatedAt(credential.getUpdatedAt())
                .build();
    }
}

package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.DeviceCredentialMapper;
import com.network_monitor.portal_service.model.dto.request.DeviceCredentialRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceCredentialResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.DeviceCredential;
import com.network_monitor.portal_service.repository.DeviceCredentialRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.DeviceCredentialService;
import com.network_monitor.portal_service.service.KafkaService;
import com.network_monitor.portal_service.util.SearchRequestUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCredentialServiceImpl
        implements DeviceCredentialService {

    private static final String TOPIC_CREDENTIAL =
            "portal.monitor.device-credential";

    private final DeviceCredentialRepository credentialRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCredentialMapper credentialMapper;
    private final KafkaService kafkaService;

    @Override
    public ApiResponse<DeviceCredentialResponse> addCredential(
            DeviceCredentialRequest request) {
        try {
            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            DeviceCredential credential = DeviceCredential.builder()
                    .device(device)
                    .credentialName(request.getCredentialName())
                    .credentialType(request.getCredentialType())
                    .protocolType(request.getProtocolType())
                    .communityString(request.getCommunityString())
                    .port(request.getPort())
                    .isActive(request.getIsActive() == null
                            ? Boolean.TRUE
                            : request.getIsActive())
                    .build();

            DeviceCredential saved =
                    credentialRepository.saveAndFlush(credential);

            sendCredentialEvent("CREATE", saved);

            return ApiResponse.success(
                    "Thêm credential thành công",
                    toResponse(saved)
            );
        } catch (Exception e) {
            log.error("Add credential failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    public ApiResponse<Map<String, Object>> createCredentialsFromExcel(
            MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ApiResponse.error("File Excel không được để trống");
        }

        int totalRows = 0;
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        List<Map<String, Object>> errors = new ArrayList<>();

        try (InputStream input = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(input)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (isEmptyRow(row, formatter, 7)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;

                try {
                    ApiResponse<DeviceCredentialResponse> response =
                            addCredential(
                                    readRequestFromExcel(row, formatter)
                            );

                    if (response != null && response.isSuccess()) {
                        successCount++;
                    } else {
                        failedCount++;

                        Map<String, Object> error = new LinkedHashMap<>();
                        error.put("row", i + 1);
                        error.put(
                                "message",
                                response == null
                                        ? "Không thể thêm credential"
                                        : response.getMessage()
                        );
                        errors.add(error);
                    }
                } catch (Exception e) {
                    failedCount++;

                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("row", i + 1);
                    error.put("message", getErrorMessage(e));
                    errors.add(error);
                }
            }
        } catch (Exception e) {
            log.error("Import credentials from Excel failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", totalRows);
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("skippedCount", skippedCount);
        result.put("errors", errors);

        return ApiResponse.success("Import Excel hoàn tất", result);
    }

    @Override
    public ApiResponse<Void> updateCredential(
            Long id,
            DeviceCredentialRequest request) {
        try {
            DeviceCredential existing =
                    credentialRepository.findById(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Không tìm thấy credential"
                                    ));

            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            DeviceCredential updated = DeviceCredential.builder()
                    .id(existing.getId())
                    .device(device)
                    .credentialName(request.getCredentialName())
                    .credentialType(request.getCredentialType())
                    .protocolType(request.getProtocolType())
                    .communityString(request.getCommunityString())
                    .port(request.getPort())
                    .isActive(request.getIsActive() == null
                            ? Boolean.TRUE
                            : request.getIsActive())
                    .build();

            DeviceCredential saved =
                    credentialRepository.saveAndFlush(updated);

            sendCredentialEvent("UPDATE", saved);

            return ApiResponse.success(
                    "Cập nhật credential thành công", null
            );
        } catch (Exception e) {
            log.error("Update credential failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    public ApiResponse<Void> deleteCredential(Long id) {
        try {
            DeviceCredential credential =
                    credentialRepository.findById(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Không tìm thấy credential"
                                    ));

            sendDeleteEvent(credential);
            credentialRepository.delete(credential);

            return ApiResponse.success("Xóa credential thành công", null);
        } catch (Exception e) {
            log.error("Delete credential failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    public ApiResponse<Void> deleteCredentialsByDeviceId(Long deviceId) {
        try {
            if (!deviceRepository.existsById(deviceId)) {
                return ApiResponse.error("Không tìm thấy thiết bị");
            }

            List<DeviceCredential> credentials =
                    credentialRepository.findByDevice_Id(deviceId);

            for (DeviceCredential credential : credentials) {
                sendDeleteEvent(credential);
                credentialRepository.delete(credential);
            }

            return ApiResponse.success(
                    credentials.isEmpty()
                            ? "Thiết bị không có credential để xóa"
                            : "Xóa credential của thiết bị thành công",
                    null
            );
        } catch (Exception e) {
            log.error(
                    "Delete credentials failed: deviceId={}",
                    deviceId,
                    e
            );
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    public Page<DeviceCredentialResponse> getCredentialsByDeviceId(
            Long deviceId,
            SearchRequest request) {

        if (!deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException("Không tìm thấy thiết bị");
        }

        SearchRequest search = request != null
                ? request
                : new SearchRequest();

        int page = SearchRequestUtils.normalizePage(search);
        int size = SearchRequestUtils.normalizeSize(search);
        String keyword = SearchRequestUtils.normalizeKeyword(search);
        String sortBy = SearchRequestUtils.normalizeSortBy(
                search, "createdAt"
        );
        String direction = SearchRequestUtils.normalizeDirection(search);

        Pageable pageable = SearchRequestUtils.toPageable(
                page, size, sortBy, direction
        );

        List<DeviceCredential> credentials =
                credentialMapper.searchCredentialsByDeviceId(
                        deviceId,
                        keyword,
                        pageable.getOffset(),
                        pageable.getPageSize(),
                        sortBy,
                        direction
                );

        long total = credentialMapper.countCredentialsByDeviceId(
                deviceId, keyword
        );

        List<DeviceCredentialResponse> responses = credentials.stream()
                .map(this::toResponse)
                .toList();

        return new PageImpl<>(responses, pageable, total);
    }

    @Override
    public ApiResponse<DeviceCredentialResponse> getCredentialById(
            Long id) {

        return credentialRepository.findById(id)
                .map(credential -> ApiResponse.success(
                        "Lấy thông tin credential thành công",
                        toResponse(credential)
                ))
                .orElseGet(() ->
                        ApiResponse.error("Không tìm thấy credential"));
    }

        @Override
        public byte[] exportCredentialsToExcel() throws IOException {
        List<DeviceCredential> credentials =
                credentialRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                Sheet sheet = workbook.createSheet("Device Credentials");

                String[] headers = {
                        "ID",
                        "Device ID",
                        "Credential Name",
                        "Credential Type",
                        "Protocol Type",
                        "Community String",
                        "Port",
                        "Is Active",
                        "Created At",
                        "Updated At"
                };

                // Header
                Row headerRow = sheet.createRow(0);

                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                }

                // Data
                int rowIndex = 1;

                for (DeviceCredential credential : credentials) {
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(
                        credential.getId() != null
                                ? credential.getId() : 0L
                );

                row.createCell(1).setCellValue(
                        credential.getDevice() != null
                                && credential.getDevice().getId() != null
                                ? credential.getDevice().getId() : 0L
                );

                row.createCell(2).setCellValue(
                        Objects.toString(credential.getCredentialName(), "")
                );

                row.createCell(3).setCellValue(
                        Objects.toString(credential.getCredentialType(), "")
                );

                row.createCell(4).setCellValue(
                        Objects.toString(credential.getProtocolType(), "")
                );

                row.createCell(5).setCellValue(
                        Objects.toString(credential.getCommunityString(), "")
                );

                if (credential.getPort() != null) {
                        row.createCell(6).setCellValue(
                                credential.getPort().doubleValue()
                        );
                } else {
                        row.createCell(6).setCellValue("");
                }

                row.createCell(7).setCellValue(
                        Boolean.TRUE.equals(credential.getIsActive())
                                ? "Yes" : "No"
                );

                row.createCell(8).setCellValue(
                        Objects.toString(credential.getCreatedAt(), "")
                );

                row.createCell(9).setCellValue(
                        Objects.toString(credential.getUpdatedAt(), "")
                );
                }

                for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                }

                workbook.write(output);
                return output.toByteArray();
        }
        }

        private DeviceCredentialRequest readRequestFromExcel(
                Row row,
                DataFormatter formatter) {

        String deviceIdValue = getCell(row, 0, formatter);
        String portValue = getCell(row, 5, formatter);
        String activeValue = getCell(row, 6, formatter);

        long deviceId;
        int port;
        Boolean isActive;

        try {
                deviceId = Long.parseLong(deviceIdValue);
        } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Device ID phải là số nguyên hợp lệ"
                );
        }

        try {
                port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Port phải là số nguyên hợp lệ"
                );
        }

        if (activeValue.isBlank()) {
                isActive = Boolean.TRUE;
        } else if (activeValue.equalsIgnoreCase("Yes")
                || activeValue.equals("1")) {
                isActive = Boolean.TRUE;
        } else if (activeValue.equalsIgnoreCase("No")
                || activeValue.equals("0")) {
                isActive = Boolean.FALSE;
        } else {
                throw new IllegalArgumentException(
                        "Cột Is Active chỉ chấp nhận Yes/No hoặc 1/0"
                );
        }

        return DeviceCredentialRequest.builder()
                .deviceId(deviceId)
                .credentialName(getCell(row, 1, formatter))
                .credentialType(getCell(row, 2, formatter))
                .protocolType(getCell(row, 3, formatter))
                .communityString(getCell(row, 4, formatter))
                .port(port)
                .isActive(isActive)
                .build();
        }

    private void sendCredentialEvent(
            String eventType,
            DeviceCredential credential) {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", credential.getId());
        data.put("deviceId", credential.getDevice().getId());
        data.put("credentialName", credential.getCredentialName());
        data.put("credentialType", credential.getCredentialType());
        data.put("protocolType", credential.getProtocolType());
        data.put("communityString", credential.getCommunityString());
        data.put("port", credential.getPort());
        data.put("isActive", credential.getIsActive());
        data.put("createdAt", credential.getCreatedAt());
        data.put("updatedAt", credential.getUpdatedAt());

        boolean sent = kafkaService.sendToMonitor(
                TOPIC_CREDENTIAL,
                eventType,
                String.valueOf(credential.getId()),
                data
        );

        if (!sent) {
            throw new IllegalStateException(
                    "Đồng bộ credential qua Kafka thất bại"
            );
        }
    }

    private void sendDeleteEvent(DeviceCredential credential) {
        boolean sent = kafkaService.sendToMonitor(
                TOPIC_CREDENTIAL,
                "DELETE",
                String.valueOf(credential.getId()),
                Map.of(
                        "credentialId", credential.getId(),
                        "deviceId", credential.getDevice().getId()
                )
        );

        if (!sent) {
            throw new IllegalStateException(
                    "Đồng bộ xóa credential thất bại: "
                            + credential.getId()
            );
        }
    }

    private DeviceCredentialResponse toResponse(
            DeviceCredential credential) {

        return DeviceCredentialResponse.builder()
                .id(credential.getId())
                .deviceId(credential.getDevice().getId())
                .credentialName(credential.getCredentialName())
                .credentialType(credential.getCredentialType())
                .protocolType(credential.getProtocolType())
                .communityString(credential.getCommunityString())
                .port(credential.getPort())
                .isActive(credential.getIsActive())
                .createdAt(credential.getCreatedAt())
                .updatedAt(credential.getUpdatedAt())
                .build();
    }

    private String getCell(
            Row row,
            int index,
            DataFormatter formatter) {

        Cell cell = row == null
                ? null
                : row.getCell(
                        index,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );

        return cell == null
                ? ""
                : formatter.formatCellValue(cell).trim();
    }

    private boolean isEmptyRow(
            Row row,
            DataFormatter formatter,
            int columnCount) {

        if (row == null) {
            return true;
        }

        for (int i = 0; i < columnCount; i++) {
            if (!getCell(row, i, formatter).isBlank()) {
                return false;
            }
        }

        return true;
    }

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

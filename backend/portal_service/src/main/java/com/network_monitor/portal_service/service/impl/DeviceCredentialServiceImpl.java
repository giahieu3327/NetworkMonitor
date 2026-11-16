
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
import com.network_monitor.portal_service.util.SearchRequestUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCredentialServiceImpl
        implements DeviceCredentialService {

    private final DeviceCredentialRepository credentialRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCredentialMapper credentialMapper;

    // ==================== CREATE ====================

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

            return ApiResponse.success(
                    "Thêm credential thành công",
                    toResponse(saved)
            );
        } catch (Exception e) {
            log.error("Add credential failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // ==================== IMPORT EXCEL ====================

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

            if (workbook.getNumberOfSheets() == 0) {
                return ApiResponse.error(
                        "File Excel không có sheet dữ liệu"
                );
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // Bỏ qua dòng tiêu đề tại index 0.
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (isEmptyRow(row, formatter, 7)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;

                try {
                    DeviceCredentialRequest request =
                            readRequestFromExcel(row, formatter);

                    ApiResponse<DeviceCredentialResponse> response =
                            addCredential(request);

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

    // ==================== UPDATE ====================

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

            credentialRepository.saveAndFlush(updated);

            return ApiResponse.success(
                    "Cập nhật credential thành công",
                    null
            );
        } catch (Exception e) {
            log.error("Update credential failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // ==================== DELETE ====================

    @Override
    public ApiResponse<Void> deleteCredential(Long id) {
        try {
            DeviceCredential credential =
                    credentialRepository.findById(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Không tìm thấy credential"
                                    ));

            credentialRepository.delete(credential);

            return ApiResponse.success(
                    "Xóa credential thành công",
                    null
            );
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

            if (!credentials.isEmpty()) {
                credentialRepository.deleteAll(credentials);
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

    // ==================== SEARCH / PAGINATION ====================

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

    // ==================== EXPORT EXCEL ====================

    @Override
    public byte[] exportCredentials() throws IOException {

        List<DeviceCredential> credentials =
                credentialRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Credentials");

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

            // Tạo style cho header.
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Tạo dòng tiêu đề.
            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Ghi dữ liệu credential.
            int rowIndex = 1;

            for (DeviceCredential credential : credentials) {
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(
                        credential.getId() != null
                                ? credential.getId()
                                : 0
                );

                row.createCell(1).setCellValue(
                        credential.getDevice() != null
                                && credential.getDevice().getId() != null
                                ? credential.getDevice().getId()
                                : 0
                );

                row.createCell(2).setCellValue(
                        getSafeString(credential.getCredentialName())
                );

                row.createCell(3).setCellValue(
                        getSafeString(credential.getCredentialType())
                );

                row.createCell(4).setCellValue(
                        getSafeString(credential.getProtocolType())
                );

                row.createCell(5).setCellValue(
                        getSafeString(credential.getCommunityString())
                );

                row.createCell(6).setCellValue(
                        credential.getPort() != null
                                ? credential.getPort()
                                : 0
                );

                row.createCell(7).setCellValue(
                        Boolean.TRUE.equals(credential.getIsActive())
                                ? "Yes"
                                : "No"
                );

                row.createCell(8).setCellValue(
                        credential.getCreatedAt() != null
                                ? credential.getCreatedAt().toString()
                                : ""
                );

                row.createCell(9).setCellValue(
                        credential.getUpdatedAt() != null
                                ? credential.getUpdatedAt().toString()
                                : ""
                );
            }

            // Tự động điều chỉnh độ rộng cột.
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);

                if (sheet.getColumnWidth(i) > 50 * 256) {
                    sheet.setColumnWidth(i, 50 * 256);
                }
            }

            // Cố định dòng tiêu đề.
            sheet.createFreezePane(0, 1);

            // Thêm bộ lọc cho các cột.
            if (!credentials.isEmpty()) {
                sheet.setAutoFilter(
                        new org.apache.poi.ss.util.CellRangeAddress(
                                0,
                                credentials.size(),
                                0,
                                headers.length - 1
                        )
                );
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }

    // ==================== DOWNLOAD IMPORT TEMPLATE ====================

    @Override
    public byte[] downloadCredentialImportTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Credentials");
            Sheet instructions = workbook.createSheet("Instructions");

            String[] headers = {
                    "Device ID",
                    "Credential Name",
                    "Credential Type",
                    "Protocol Type",
                    "Community String",
                    "Port",
                    "Is Active"
            };

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Header của sheet Credentials.
            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Không điền Device ID giả định để tránh import nhầm thiết bị.

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Nội dung hướng dẫn.
            String[][] instructionData = {
                    {"Field", "Description"},
                    {
                            "Device ID",
                            "ID của thiết bị đã tồn tại trong hệ thống"
                    },
                    {"Credential Name", "Tên credential"},
                    {
                            "Credential Type",
                            "Loại credential, ví dụ SNMP"
                    },
                    {
                            "Protocol Type",
                            "Giao thức, ví dụ SNMPV2C"
                    },
                    {
                            "Community String",
                            "SNMP community string hoặc thông tin tương ứng"
                    },
                    {"Port", "Cổng giao tiếp, ví dụ 161"},
                    {
                            "Is Active",
                            "Chỉ chấp nhận Yes/No hoặc 1/0; để trống mặc định Yes"
                    },
                    {
                            "Note",
                            "Nhập dữ liệu từ dòng 2 trong sheet Credentials"
                    }
            };

            for (int i = 0; i < instructionData.length; i++) {
                Row row = instructions.createRow(i);

                for (int j = 0; j < instructionData[i].length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(instructionData[i][j]);

                    if (i == 0) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }

            instructions.autoSizeColumn(0);
            instructions.setColumnWidth(1, 70 * 256);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    // ==================== EXCEL MAPPING ====================

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

    // ==================== RESPONSE MAPPING ====================

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

    // ==================== EXCEL HELPERS ====================

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

    private String getSafeString(String value) {
        return value != null ? value : "";
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

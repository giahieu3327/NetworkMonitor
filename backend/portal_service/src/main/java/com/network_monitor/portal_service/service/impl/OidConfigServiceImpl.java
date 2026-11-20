
package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.OidConfigMapper;
import com.network_monitor.portal_service.model.dto.request.OidConfigRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.OidConfigResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.DeviceInterface;
import com.network_monitor.portal_service.model.entity.OidConfig;
import com.network_monitor.portal_service.repository.DeviceInterfaceRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.OidConfigRepository;
import com.network_monitor.portal_service.service.OidConfigService;
import com.network_monitor.portal_service.service.SnmpService;
import com.network_monitor.portal_service.util.SearchRequestUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
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
public class OidConfigServiceImpl implements OidConfigService {

    private static final String OID_KIND_SINGLE = "SINGLE";
    private static final String OID_KIND_TABLE_COLUMN = "TABLE_COLUMN";

    private final OidConfigRepository oidConfigRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceInterfaceRepository deviceInterfaceRepository;
    private final OidConfigMapper oidConfigMapper;
    private final SnmpService snmpService;

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public ApiResponse<OidConfigResponse> createOidConfig(
            OidConfigRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException(
                        "OID config request không được để trống"
                );
            }

            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            String oidKind = normalizeOidKind(request.getOidKind());

            OidConfig config = OidConfig.builder()
                    .metricScope(request.getMetricScope())
                    .metricType(request.getMetricType())
                    .oidKind(oidKind)
                    .oidPattern(request.getOidPattern())
                    .dataType(request.getDataType())
                    .multiplier(request.getMultiplier() == null
                            ? 1.0
                            : request.getMultiplier())
                    .device(device)
                    .isActive(request.getIsActive() == null
                            ? Boolean.TRUE
                            : request.getIsActive())
                    .build();

            setInterface(
                    config,
                    device,
                    request.getInterfaceIndex()
            );

            OidConfig saved = oidConfigRepository.saveAndFlush(config);

            snmpService.reloadConfigs();

            return ApiResponse.success(
                    "Tạo OID config thành công",
                    toResponse(saved)
            );

        } catch (Exception e) {
            log.error("Create OID config failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =========================================================
    // IMPORT EXCEL
    //
    // Column order:
    // 0 Metric Scope
    // 1 Metric Type
    // 2 OID Kind
    // 3 OID Pattern
    // 4 Data Type
    // 5 Multiplier
    // 6 Device ID
    // 7 Interface Index
    // 8 Is Active
    // =========================================================

        @Override
        public ApiResponse<Map<String, Object>> createOidConfigsFromExcel(
                Long id,
                MultipartFile file) {

        if (id == null || !deviceRepository.existsById(id)) {
                return ApiResponse.error("Không tìm thấy thiết bị");
        }

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

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (isEmptyRow(row, formatter, 8)) {
                        skippedCount++;
                        continue;
                }

                totalRows++;

                try {
                        OidConfigRequest request =
                                readRequestFromExcel(row, formatter);

                        // Lấy Device ID từ URL, không lấy từ Excel.
                        request.setDeviceId(id);

                        ApiResponse<OidConfigResponse> response =
                                createOidConfig(request);

                        if (response != null && response.isSuccess()) {
                        successCount++;
                        } else {
                        failedCount++;

                        Map<String, Object> error = new LinkedHashMap<>();
                        error.put("row", i + 1);
                        error.put(
                                "message",
                                response == null
                                        ? "Không thể tạo OID config"
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
                log.error(
                        "Import OID configs from Excel failed: deviceId={}",
                        id,
                        e
                );

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

    // =========================================================
    // UPDATE
    // =========================================================

    @Override
    public ApiResponse<Void> updateOidConfig(
            Long id,
            OidConfigRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException(
                        "OID config request không được để trống"
                );
            }

            OidConfig config = oidConfigRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy OID config"
                            ));

            if (request.getMetricScope() != null) {
                config.setMetricScope(request.getMetricScope());
            }

            if (request.getMetricType() != null) {
                config.setMetricType(request.getMetricType());
            }

            if (request.getOidKind() != null) {
                config.setOidKind(
                        normalizeOidKind(request.getOidKind())
                );
            }

            if (request.getOidPattern() != null) {
                config.setOidPattern(request.getOidPattern());
            }

            if (request.getDataType() != null) {
                config.setDataType(request.getDataType());
            }

            if (request.getMultiplier() != null) {
                config.setMultiplier(request.getMultiplier());
            }

            if (request.getIsActive() != null) {
                config.setIsActive(request.getIsActive());
            }

            if (request.getDeviceId() != null) {
                Device device = deviceRepository.findById(
                        request.getDeviceId()
                ).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy thiết bị"
                        ));

                config.setDevice(device);

                setInterface(
                        config,
                        device,
                        request.getInterfaceIndex()
                );

            } else if (request.getInterfaceIndex() != null) {
                setInterface(
                        config,
                        config.getDevice(),
                        request.getInterfaceIndex()
                );
            }

            oidConfigRepository.saveAndFlush(config);

            snmpService.reloadConfigs();

            return ApiResponse.success(
                    "Cập nhật OID config thành công",
                    null
            );

        } catch (Exception e) {
            log.error("Update OID config failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =========================================================
    // DELETE
    // =========================================================

    @Override
    public ApiResponse<Void> deleteOidConfig(Long id) {
        try {
            OidConfig config = oidConfigRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy OID config"
                            ));

            oidConfigRepository.delete(config);

            snmpService.reloadConfigs();

            return ApiResponse.success(
                    "Xóa OID config thành công",
                    null
            );

        } catch (Exception e) {
            log.error("Delete OID config failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    public ApiResponse<Void> deleteOidConfigsByDeviceId(Long deviceId) {
        try {
            if (!deviceRepository.existsById(deviceId)) {
                return ApiResponse.error("Không tìm thấy thiết bị");
            }

            List<OidConfig> configs =
                    oidConfigRepository.findByDevice_Id(deviceId);

            if (!configs.isEmpty()) {
                oidConfigRepository.deleteAll(configs);
            }

            snmpService.reloadConfigs();
            
            return ApiResponse.success(
                    configs.isEmpty()
                            ? "Thiết bị không có OID config để xóa"
                            : "Xóa OID config của thiết bị thành công",
                    null
            );

        } catch (Exception e) {
            log.error(
                    "Delete OID configs failed: deviceId={}",
                    deviceId,
                    e
            );
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    public ApiResponse<OidConfigResponse> getOidConfigById(Long id) {
        return oidConfigRepository.findById(id)
                .map(config -> ApiResponse.success(
                        "Lấy thông tin OID config thành công",
                        toResponse(config)
                ))
                .orElseGet(() ->
                        ApiResponse.error("Không tìm thấy OID config"));
    }

    // =========================================================
    // GET BY DEVICE ID WITH SEARCH AND PAGINATION
    // =========================================================

    @Override
    public Page<OidConfigResponse> getOidConfigsByDeviceId(
            Long deviceId,
            SearchRequest request) {

        SearchRequest search = request != null
                ? request
                : new SearchRequest();

        int page = SearchRequestUtils.normalizePage(search);
        int size = SearchRequestUtils.normalizeSize(search);
        String keyword = SearchRequestUtils.normalizeKeyword(search);

        String sortBy = SearchRequestUtils.normalizeSortBy(
                search,
                "createdAt"
        );

        String direction =
                SearchRequestUtils.normalizeDirection(search);

        Pageable pageable = SearchRequestUtils.toPageable(
                page,
                size,
                sortBy,
                direction
        );

        List<OidConfig> configs =
                oidConfigMapper.findByDeviceId(
                        deviceId,
                        keyword,
                        pageable.getOffset(),
                        pageable.getPageSize(),
                        sortBy,
                        direction
                );

        long total = oidConfigMapper.countByDeviceId(
                deviceId,
                keyword
        );

        List<OidConfigResponse> responses = configs.stream()
                .map(this::toResponse)
                .toList();

        return new PageImpl<>(responses, pageable, total);
    }

    // =========================================================
    // EXPORT EXCEL
    // =========================================================

        @Override
        public byte[] exportOidConfigs(Long deviceId) throws IOException {

        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
                throw new IllegalArgumentException("Không tìm thấy thiết bị");
        }

        // Chỉ lấy OID Config thuộc thiết bị được chỉ định.
        List<OidConfig> configs =
                oidConfigRepository.findByDevice_Id(deviceId);

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                Sheet sheet = workbook.createSheet("OID Configs");

                String[] headers = {
                        "ID",
                        "Device ID",
                        "Metric Scope",
                        "Metric Type",
                        "OID Kind",
                        "OID Pattern",
                        "Data Type",
                        "Multiplier",
                        "Interface Index",
                        "Is Active",
                        "Created At",
                        "Updated At"
                };

                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                Row headerRow = sheet.createRow(0);

                for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                }

                int rowIndex = 1;

                for (OidConfig config : configs) {
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(
                        config.getId() != null
                                ? config.getId()
                                : 0
                );

                row.createCell(1).setCellValue(
                        config.getDevice() != null
                                && config.getDevice().getId() != null
                                ? config.getDevice().getId()
                                : 0
                );

                row.createCell(2).setCellValue(
                        getSafeString(config.getMetricScope())
                );

                row.createCell(3).setCellValue(
                        getSafeString(config.getMetricType())
                );

                row.createCell(4).setCellValue(
                        getSafeString(config.getOidKind())
                );

                row.createCell(5).setCellValue(
                        getSafeString(config.getOidPattern())
                );

                row.createCell(6).setCellValue(
                        getSafeString(config.getDataType())
                );

                row.createCell(7).setCellValue(
                        config.getMultiplier() != null
                                ? config.getMultiplier().doubleValue()
                                : 1.0
                );

                if (config.getInterfaceIndex() != null) {
                        row.createCell(8).setCellValue(
                                config.getInterfaceIndex()
                        );
                } else {
                        row.createCell(8).setCellValue("");
                }

                row.createCell(9).setCellValue(
                        Boolean.TRUE.equals(config.getIsActive())
                                ? "Yes"
                                : "No"
                );

                row.createCell(10).setCellValue(
                        config.getCreatedAt() != null
                                ? config.getCreatedAt().toString()
                                : ""
                );

                row.createCell(11).setCellValue(
                        config.getUpdatedAt() != null
                                ? config.getUpdatedAt().toString()
                                : ""
                );
                }

                for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);

                if (sheet.getColumnWidth(i) > 50 * 256) {
                        sheet.setColumnWidth(i, 50 * 256);
                }
                }

                sheet.createFreezePane(0, 1);

                if (!configs.isEmpty()) {
                sheet.setAutoFilter(
                        new CellRangeAddress(
                                0,
                                configs.size(),
                                0,
                                headers.length - 1
                        )
                );
                }

                workbook.write(output);
                return output.toByteArray();
        }
        }

    // =========================================================
    // DOWNLOAD EXCEL IMPORT TEMPLATE
    // =========================================================

        @Override
        public byte[] downloadOidConfigImportTemplate() throws IOException {

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                Sheet dataSheet = workbook.createSheet("OID Configs");

                // Device ID được lấy từ URL nên không cần cột này.
                String[] headers = {
                        "Metric Scope",
                        "Metric Type",
                        "OID Kind",
                        "OID Pattern",
                        "Data Type",
                        "Multiplier",
                        "Interface Index",
                        "Is Active"
                };

                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                Row headerRow = dataSheet.createRow(0);

                for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                }

                for (int i = 0; i < headers.length; i++) {
                dataSheet.autoSizeColumn(i);
                }

                Sheet instructionsSheet =
                        workbook.createSheet("Instructions");

                String[][] instructions = {
                        {"Column", "Description"},
                        {
                                "Metric Scope",
                                "Phạm vi metric, ví dụ DEVICE hoặc INTERFACE"
                        },
                        {
                                "Metric Type",
                                "Loại metric cần giám sát"
                        },
                        {
                                "OID Kind",
                                "SINGLE: lấy một OID bằng SNMP GET; "
                                        + "TABLE_COLUMN: lấy cột OID bằng SNMP WALK"
                        },
                        {
                                "OID Pattern",
                                "OID cần thu thập. Với TABLE_COLUMN, "
                                        + "nhập OID gốc của cột, không thêm interface index"
                        },
                        {
                                "Data Type",
                                "Kiểu dữ liệu theo cấu hình hệ thống"
                        },
                        {
                                "Multiplier",
                                "Hệ số nhân dạng số; để trống sẽ dùng 1.0"
                        },
                        {
                                "Interface Index",
                                "Index interface đã tồn tại của thiết bị; "
                                        + "có thể để trống nếu không áp dụng "
                                        + "hoặc muốn lấy toàn bộ interface"
                        },
                        {
                                "Is Active",
                                "Yes/No hoặc 1/0; để trống sẽ mặc định Yes"
                        },
                        {
                                "OID Kind values",
                                "Chỉ chấp nhận SINGLE hoặc TABLE_COLUMN"
                        },
                        {
                                "Note",
                                "Dòng đầu tiên của sheet OID Configs là tiêu đề; "
                                        + "không sửa thứ tự cột. Device ID được "
                                        + "xác định từ URL import."
                        }
                };

                for (int i = 0; i < instructions.length; i++) {
                Row row = instructionsSheet.createRow(i);

                for (int j = 0; j < instructions[i].length; j++) {
                        Cell cell = row.createCell(j);
                        cell.setCellValue(instructions[i][j]);

                        if (i == 0) {
                        cell.setCellStyle(headerStyle);
                        }
                }
                }

                instructionsSheet.setColumnWidth(0, 24 * 256);
                instructionsSheet.setColumnWidth(1, 100 * 256);

                workbook.write(output);
                return output.toByteArray();
        }
        }

    // =========================================================
    // EXCEL ROW -> REQUEST
    // =========================================================

        private OidConfigRequest readRequestFromExcel(
                Row row,
                DataFormatter formatter) {

        String metricScope = getCell(row, 0, formatter);
        String metricType = getCell(row, 1, formatter);
        String oidKind = getCell(row, 2, formatter);
        String oidPattern = getCell(row, 3, formatter);
        String dataType = getCell(row, 4, formatter);
        String multiplierValue = getCell(row, 5, formatter);
        String interfaceIndexValue = getCell(row, 6, formatter);
        String activeValue = getCell(row, 7, formatter);

        if (metricScope.isBlank()) {
                throw new IllegalArgumentException(
                        "Metric Scope không được để trống"
                );
        }

        if (metricType.isBlank()) {
                throw new IllegalArgumentException(
                        "Metric Type không được để trống"
                );
        }

        oidKind = normalizeOidKind(oidKind);

        if (oidPattern.isBlank()) {
                throw new IllegalArgumentException(
                        "OID Pattern không được để trống"
                );
        }

        if (dataType.isBlank()) {
                throw new IllegalArgumentException(
                        "Data Type không được để trống"
                );
        }

        double multiplier = 1.0;

        if (!multiplierValue.isBlank()) {
                try {
                multiplier = Double.parseDouble(multiplierValue);

                if (!Double.isFinite(multiplier)) {
                        throw new NumberFormatException();
                }
                } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Multiplier phải là số hợp lệ"
                );
                }
        }

        Integer interfaceIndex = null;

        if (!interfaceIndexValue.isBlank()) {
                try {
                interfaceIndex = Integer.parseInt(interfaceIndexValue);

                if (interfaceIndex <= 0) {
                        throw new NumberFormatException();
                }
                } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Interface Index phải là số nguyên dương"
                );
                }
        }

        Boolean isActive;

        if (activeValue.isBlank()
                || activeValue.equalsIgnoreCase("Yes")
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

        return OidConfigRequest.builder()
                .metricScope(metricScope)
                .metricType(metricType)
                .oidKind(oidKind)
                .oidPattern(oidPattern)
                .dataType(dataType)
                .multiplier(multiplier)
                .interfaceIndex(interfaceIndex)
                .isActive(isActive)
                .build();
        }

    // =========================================================
    // SET DEVICE INTERFACE
    // =========================================================

    private void setInterface(
            OidConfig config,
            Device device,
            Integer interfaceIndex) {

        config.setInterfaceIndex(interfaceIndex);

        if (interfaceIndex == null) {
            config.setDeviceInterface(null);
            return;
        }

        if (interfaceIndex <= 0) {
            throw new IllegalArgumentException(
                    "Interface Index phải là số nguyên dương"
            );
        }

        DeviceInterface.DeviceInterfaceId id =
                new DeviceInterface.DeviceInterfaceId(
                        device.getId(),
                        interfaceIndex
                );

        DeviceInterface deviceInterface =
                deviceInterfaceRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Không tìm thấy interface "
                                                + interfaceIndex
                                                + " của thiết bị "
                                                + device.getId()
                                ));

        config.setDeviceInterface(deviceInterface);
    }

    // =========================================================
    // ENTITY -> RESPONSE
    // =========================================================

    private OidConfigResponse toResponse(OidConfig config) {
        return OidConfigResponse.builder()
                .id(config.getId())
                .metricScope(config.getMetricScope())
                .metricType(config.getMetricType())
                .oidKind(config.getOidKind())
                .oidPattern(config.getOidPattern())
                .dataType(config.getDataType())
                .multiplier(config.getMultiplier())
                .deviceId(
                        config.getDevice() == null
                                ? null
                                : config.getDevice().getId()
                )
                .interfaceIndex(config.getInterfaceIndex())
                .isActive(config.getIsActive())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    // =========================================================
    // OID KIND VALIDATION
    // =========================================================

    private String normalizeOidKind(String oidKind) {
        if (oidKind == null || oidKind.isBlank()) {
            throw new IllegalArgumentException(
                    "OID Kind không được để trống. "
                            + "Chỉ chấp nhận SINGLE hoặc TABLE_COLUMN"
            );
        }

        String normalized = oidKind.trim().toUpperCase();

        if (!OID_KIND_SINGLE.equals(normalized)
                && !OID_KIND_TABLE_COLUMN.equals(normalized)) {
            throw new IllegalArgumentException(
                    "OID Kind không hợp lệ: " + oidKind
                            + ". Chỉ chấp nhận SINGLE hoặc TABLE_COLUMN"
            );
        }

        return normalized;
    }

    // =========================================================
    // EXCEL HELPERS
    // =========================================================

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


package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.health.Health;
import com.network_monitor.portal_service.mapper.DeviceMapper;
import com.network_monitor.portal_service.model.dto.request.DeviceRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.DeviceService;
import com.network_monitor.portal_service.service.SnmpService;
import com.network_monitor.portal_service.util.SearchRequestUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceMapper deviceMapper;
    private final Health health;
    private final SnmpService snmpService;

    // =========================================================
    // CREATE DEVICE
    // =========================================================

    @Override
    public ApiResponse<DeviceResponse> createDevice(DeviceRequest request) {
        try {
            Device device = Device.builder()
                    .deviceName(request.getDeviceName())
                    .ipAddress(request.getIpAddress())
                    .deviceType(request.getDeviceType())
                    .status(
                            health.checkPing(request.getIpAddress())
                                    ? "UP"
                                    : "DOWN"
                    )
                    .build();

            Device saved = deviceRepository.saveAndFlush(device);

            snmpService.reloadConfigs();

            return ApiResponse.success(
                    "Thêm thiết bị thành công",
                    toResponse(saved)
            );

        } catch (Exception e) {
            log.error("Create device failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =========================================================
    // IMPORT DEVICES FROM EXCEL
    // =========================================================

    @Override
    public ApiResponse<Map<String, Object>> createDevicesFromExcel(
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

            Sheet sheet = workbook.getSheet("Devices");

            if (sheet == null && workbook.getNumberOfSheets() > 0) {
                sheet = workbook.getSheetAt(0);
            }

            if (sheet == null) {
                return ApiResponse.error(
                        "File Excel không có sheet dữ liệu"
                );
            }

            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (isEmptyRow(row, formatter, 3)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;

                try {
                    DeviceRequest request = readRequestFromExcel(
                            row, formatter
                    );

                    if (request.getDeviceName().isBlank()
                            || request.getIpAddress().isBlank()
                            || request.getDeviceType().isBlank()) {

                        throw new IllegalArgumentException(
                                "Device Name, IP Address và Device Type "
                                        + "không được để trống"
                        );
                    }

                    if (!request.getIpAddress().matches(
                            "^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}"
                                    + "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$"
                    )) {
                        throw new IllegalArgumentException(
                                "Địa chỉ IPv4 không hợp lệ"
                        );
                    }

                    ApiResponse<DeviceResponse> response =
                            createDevice(request);

                    if (response != null && response.isSuccess()) {
                        successCount++;
                    } else {
                        failedCount++;

                        addImportError(
                                errors,
                                i + 1,
                                response == null
                                        ? "Không thể tạo thiết bị"
                                        : response.getMessage()
                        );
                    }

                } catch (Exception e) {
                    failedCount++;

                    addImportError(
                            errors,
                            i + 1,
                            getErrorMessage(e)
                    );
                }
            }

        } catch (Exception e) {
            log.error("Import devices failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("totalRows", totalRows);
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("skippedCount", skippedCount);
        result.put("errors", errors);

        return ApiResponse.success(
                "Import Excel hoàn tất",
                result
        );
    }

    private void addImportError(
            List<Map<String, Object>> errors,
            int row,
            String message) {

        Map<String, Object> error = new LinkedHashMap<>();

        error.put("row", row);
        error.put("message", message);

        errors.add(error);
    }

    // =========================================================
    // UPDATE DEVICE
    // =========================================================

    @Override
    public ApiResponse<Void> updateDevice(
            Long id,
            DeviceRequest request) {

        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            device.setDeviceName(request.getDeviceName());
            device.setIpAddress(request.getIpAddress());
            device.setDeviceType(request.getDeviceType());

            deviceRepository.saveAndFlush(device);

            snmpService.reloadConfigs();

            return ApiResponse.success(
                    "Cập nhật thiết bị thành công",
                    null
            );

        } catch (Exception e) {
            log.error("Update device failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =========================================================
    // DELETE DEVICE
    // =========================================================

    @Override
    public ApiResponse<Void> deleteDevice(Long id) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            deviceRepository.delete(device);
            deviceRepository.flush();

            snmpService.reloadConfigs();

            return ApiResponse.success(
                    "Xóa thiết bị thành công",
                    null
            );

        } catch (Exception e) {
            log.error("Delete device failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =========================================================
    // DELETE ALL DEVICES
    // =========================================================

    @Override
    public ApiResponse<Void> deleteAllDevices() {
        try {
            deviceRepository.deleteAll();
            deviceRepository.flush();

            snmpService.reloadConfigs();

            return ApiResponse.success(
                    "Xóa tất cả thiết bị thành công",
                    null
            );

        } catch (Exception e) {
            log.error("Delete all devices failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =========================================================
    // SEARCH DEVICES WITH PAGINATION
    // =========================================================

    @Override
    public Page<DeviceResponse> getDevices(SearchRequest request) {

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

        long offset = (long) page * size;

        // Chỉ truy vấn PostgreSQL, không Ping hoặc SNMP.
        List<Device> devices = deviceMapper.searchDevices(
                keyword,
                offset,
                size,
                sortBy,
                direction
        );

        List<DeviceResponse> responses = devices.stream()
                .map(this::toResponse)
                .toList();

        long total = deviceMapper.countSearchDevices(keyword);

        return new PageImpl<>(
                responses,
                SearchRequestUtils.toPageable(
                        page,
                        size,
                        sortBy,
                        direction
                ),
                total
        );
    }

    // =========================================================
    // GET DEVICE BY ID
    // =========================================================

    @Override
    public ApiResponse<DeviceResponse> getDeviceById(Long id) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            // Chỉ lấy dữ liệu đã lưu trong PostgreSQL.
            // Không gọi Ping hoặc SNMP Discovery.

            return ApiResponse.success(
                    "Lấy thông tin thiết bị thành công",
                    toResponse(device)
            );

        } catch (Exception e) {
            log.error("Get device failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    // =========================================================
    // EXPORT DEVICES - APACHE POI
    // =========================================================

    @Override
    public byte[] exportDevices() throws IOException {

        List<Device> devices = deviceRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Devices");

            String[] headers = {
                    "ID",
                    "Device Name",
                    "IP Address",
                    "Device Type",
                    "Status",
                    "Created At",
                    "Updated At"
            };

            CellStyle headerStyle = createHeaderStyle(workbook);

            Row header = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;

            for (Device device : devices) {
                Row row = sheet.createRow(rowIndex++);

                setCell(row, 0, device.getId());
                setCell(row, 1, device.getDeviceName());
                setCell(row, 2, device.getIpAddress());
                setCell(row, 3, device.getDeviceType());
                setCell(row, 4, device.getStatus());
                setCell(row, 5, device.getCreatedAt());
                setCell(row, 6, device.getUpdatedAt());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);

                if (sheet.getColumnWidth(i) > 50 * 256) {
                    sheet.setColumnWidth(i, 50 * 256);
                }
            }

            sheet.createFreezePane(0, 1);

            sheet.setAutoFilter(new CellRangeAddress(
                    0,
                    devices.size(),
                    0,
                    headers.length - 1
            ));

            workbook.write(output);

            return output.toByteArray();
        }
    }

    private void setCell(Row row, int index, Object value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value.toString());
    }

    // =========================================================
    // DOWNLOAD EXCEL IMPORT TEMPLATE
    // =========================================================

    @Override
    public byte[] downloadDeviceImportTemplate() throws IOException {

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet sheet = workbook.createSheet("Devices");

            String[] headers = {
                    "Device Name",
                    "IP Address",
                    "Device Type"
            };

            Row header = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            sheet.setColumnWidth(0, 30 * 256);
            sheet.setColumnWidth(1, 22 * 256);
            sheet.setColumnWidth(2, 25 * 256);

            sheet.createFreezePane(0, 1);

            sheet.setAutoFilter(new CellRangeAddress(
                    0, 0, 0, headers.length - 1
            ));

            // Instructions
            Sheet instructions = workbook.createSheet("Instructions");

            String[][] data = {
                    {"CỘT", "HƯỚNG DẪN"},
                    {
                            "Device Name",
                            "Tên thiết bị, không được để trống và không trùng lặp."
                    },
                    {
                            "IP Address",
                            "Địa chỉ IPv4 hợp lệ, không được để trống và không trùng lặp."
                    },
                    {
                            "Device Type",
                            "Loại thiết bị: ROUTER, SWITCH, FIREWALL, FORTIGATE, CISCO, PEPLINK..."
                    },
                    {
                            "Cách nhập",
                            "Nhập dữ liệu bắt đầu từ hàng 2 trong sheet Devices."
                    },
                    {
                            "Tên cột",
                            "Không thay đổi tên hoặc thứ tự 3 cột."
                    },
                    {
                            "Dòng trống",
                            "Các dòng hoàn toàn trống sẽ được bỏ qua."
                    },
                    {
                            "Status",
                            "Hệ thống tự động kiểm tra ICMP Ping để xác định UP hoặc DOWN."
                    },
                    {
                            "SNMP Credential",
                            "Sau khi thêm thiết bị, cấu hình SNMP Credential riêng cho từng thiết bị."
                    },
                    {
                            "SNMP Discovery",
                            "Thông tin Discovery được lấy trực tiếp từ thiết bị khi có yêu cầu, không lưu vào bảng Devices."
                    }
            };

            for (int i = 0; i < data.length; i++) {
                Row row = instructions.createRow(i);

                for (int j = 0; j < data[i].length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(data[i][j]);

                    if (i == 0) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }

            instructions.setColumnWidth(0, 25 * 256);
            instructions.setColumnWidth(1, 100 * 256);
            instructions.createFreezePane(0, 1);

            workbook.write(output);

            return output.toByteArray();
        }
    }

    // =========================================================
    // UPDATE DEVICE STATUS - ICMP PING
    // =========================================================

    @Override
    public ApiResponse<Void> updateStatus(Long id, String status) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            device.setStatus(
                    health.checkPing(device.getIpAddress())
                            ? "UP"
                            : "DOWN"
            );

            deviceRepository.saveAndFlush(device);

            return ApiResponse.success(
                    "Cập nhật trạng thái thiết bị thành công",
                    null
            );

        } catch (Exception e) {
            log.error("Update device status failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

        // =========================================================
        // GET DEVICE INFO - SNMP DISCOVERY
        // =========================================================

        @Override
        public ApiResponse<Map<String, Object>> getDeviceInfo(Long id) {
        try {
                // Kiểm tra thiết bị có tồn tại trong database
                Device device = deviceRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Không tìm thấy thiết bị"));

                // Gọi SnmpService để lấy thông tin DEVICE scope
                Map<String, Object> snmpResult = snmpService.pollDeviceScope(id);

                // Tạo kết quả trả về
                Map<String, Object> result = new LinkedHashMap<>();

                result.put("id", device.getId());
                result.put("deviceName", device.getDeviceName());
                result.put("ipAddress", device.getIpAddress());
                result.put("deviceType", device.getDeviceType());
                result.put("status", device.getStatus());

                // Thông tin lấy từ SNMP
                result.put("snmpInfo", snmpResult.get("device"));

                return ApiResponse.success(
                        "Lấy thông tin thiết bị thành công",
                        result
                );

        } catch (Exception e) {
                log.error("Get device info failed: id={}", id, e);

                return ApiResponse.error(getErrorMessage(e));
        }
        }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    private DeviceRequest readRequestFromExcel(
            Row row,
            DataFormatter formatter) {

        return DeviceRequest.builder()
                .deviceName(getCell(row, 0, formatter))
                .ipAddress(getCell(row, 1, formatter))
                .deviceType(getCell(row, 2, formatter))
                .build();
    }

    private DeviceResponse toResponse(Device device) {

        return DeviceResponse.builder()
                .id(device.getId())
                .deviceName(device.getDeviceName())
                .ipAddress(device.getIpAddress())
                .deviceType(device.getDeviceType())
                .status(device.getStatus())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }

    private CellStyle createHeaderStyle(Workbook workbook) {

        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setBold(true);

        style.setFont(font);

        style.setFillForegroundColor(
                IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()
        );

        style.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        return style;
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

        while (cause.getCause() != null
                && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        return cause.getMessage() != null
                ? cause.getMessage()
                : "Đã xảy ra lỗi không xác định";
    }
}

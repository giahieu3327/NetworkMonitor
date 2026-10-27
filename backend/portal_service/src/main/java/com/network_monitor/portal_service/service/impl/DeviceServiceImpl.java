
package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.DeviceMapper;
import com.network_monitor.portal_service.model.dto.request.DeviceRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceResponse;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.DeviceService;
import com.network_monitor.portal_service.service.KafkaService;
import com.network_monitor.portal_service.util.SearchRequestUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
public class DeviceServiceImpl implements DeviceService {

    private static final String TOPIC_DEVICE = "portal.monitor.device";

    private final DeviceRepository deviceRepository;
    private final DeviceMapper deviceMapper;
    private final KafkaService kafkaService;

    @Override
    public ApiResponse<DeviceResponse> createDevice(DeviceRequest request) {
        try {
            Device device = Device.builder()
                    .deviceName(request.getDeviceName())
                    .ipAddress(request.getIpAddress())
                    .deviceType(request.getDeviceType())
                    .status("UNKNOWN")
                    .build();

            Device saved = deviceRepository.saveAndFlush(device);

            sendDeviceEvent("CREATE", saved);

            return ApiResponse.success(
                    "Thêm thiết bị thành công",
                    toResponse(saved)
            );
        } catch (Exception e) {
            log.error("Create device failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

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

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (isEmptyRow(row, formatter, 3)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;

                ApiResponse<DeviceResponse> response =
                        createDevice(readRequestFromExcel(row, formatter));

                if (response != null && response.isSuccess()) {
                    successCount++;
                } else {
                    failedCount++;

                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("row", i + 1);
                    error.put(
                            "message",
                            response == null
                                    ? "Không thể tạo thiết bị"
                                    : response.getMessage()
                    );
                    errors.add(error);
                }
            }
        } catch (Exception e) {
            log.error("Import devices from Excel failed", e);
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
    public ApiResponse<Void> updateDevice(Long id, DeviceRequest request) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            device.setDeviceName(request.getDeviceName());
            device.setIpAddress(request.getIpAddress());
            device.setDeviceType(request.getDeviceType());

            Device saved = deviceRepository.saveAndFlush(device);

            sendDeviceEvent("UPDATE", saved);

            return ApiResponse.success(
                    "Cập nhật thiết bị thành công", null
            );
        } catch (Exception e) {
            log.error("Update device failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    public ApiResponse<Void> deleteDevice(Long id) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            sendDeleteEvent(device);
            deviceRepository.delete(device);

            return ApiResponse.success("Xóa thiết bị thành công", null);
        } catch (Exception e) {
            log.error("Delete device failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    public ApiResponse<Void> deleteAllDevices() {
        try {
            for (Device device : deviceRepository.findAll()) {
                sendDeleteEvent(device);
                deviceRepository.delete(device);
            }

            return ApiResponse.success(
                    "Xóa tất cả thiết bị thành công", null
            );
        } catch (Exception e) {
            log.error("Delete all devices failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

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

        List<Device> devices = deviceMapper.searchDevices(
                keyword,
                (long) page * size,
                size,
                sortBy,
                direction
        );

        long total = deviceMapper.countSearchDevices(keyword);

        List<DeviceResponse> responses = devices.stream()
                .map(this::toResponse)
                .toList();

        return new PageImpl<>(
                responses,
                SearchRequestUtils.toPageable(
                        page, size, sortBy, direction
                ),
                total
        );
    }

    @Override
    public ApiResponse<DeviceResponse> getDeviceById(Long id) {
        return deviceRepository.findById(id)
                .map(device -> ApiResponse.success(
                        "Lấy thông tin thiết bị thành công",
                        toResponse(device)
                ))
                .orElseGet(() ->
                        ApiResponse.error("Không tìm thấy thiết bị"));
    }

    @Override
    public byte[] exportDevicesToExcel() throws IOException {
        List<Device> devices = deviceRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
            ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Devices");

            // Header
            String[] headers = {
                    "ID",
                    "Device Name",
                    "Ip Address",
                    "Device Type",
                    "Status",
                    "Created At",
                    "Updated At"
            };

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

            for (Device device : devices) {
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(
                        device.getId() != null ? device.getId() : 0L
                );
                row.createCell(1).setCellValue(
                        Objects.toString(device.getDeviceName(), "")
                );
                row.createCell(2).setCellValue(
                        Objects.toString(device.getIpAddress(), "")
                );
                row.createCell(3).setCellValue(
                        Objects.toString(device.getDeviceType(), "")
                );
                row.createCell(4).setCellValue(
                        Objects.toString(device.getStatus(), "")
                );
                row.createCell(5).setCellValue(
                        Objects.toString(device.getCreatedAt(), "")
                );
                row.createCell(6).setCellValue(
                        Objects.toString(device.getUpdatedAt(), "")
                );
            }

            // Adjust column widths
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }

    @Override
    public ApiResponse<Void> updateStatus(Long id, String status) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            device.setStatus(status);
            deviceRepository.saveAndFlush(device);

            return ApiResponse.success(
                    "Cập nhật trạng thái thành công", null
            );
        } catch (Exception e) {
            log.error("Update device status failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    private DeviceRequest readRequestFromExcel(
            Row row,
            DataFormatter formatter) {

        return DeviceRequest.builder()
                .deviceName(getCell(row, 0, formatter))
                .ipAddress(getCell(row, 1, formatter))
                .deviceType(getCell(row, 2, formatter))
                .build();
    }

    private void sendDeviceEvent(String eventType, Device device) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceId", device.getId());
        data.put("deviceName", device.getDeviceName());
        data.put("ipAddress", device.getIpAddress());
        data.put("deviceType", device.getDeviceType());
        data.put("status", device.getStatus());

        boolean sent = kafkaService.sendToMonitor(
                TOPIC_DEVICE,
                eventType,
                String.valueOf(device.getId()),
                data
        );

        if (!sent) {
            throw new IllegalStateException(
                    "Đồng bộ thiết bị qua Kafka thất bại"
            );
        }
    }

    private void sendDeleteEvent(Device device) {
        boolean sent = kafkaService.sendToMonitor(
                TOPIC_DEVICE,
                "DELETE",
                String.valueOf(device.getId()),
                Map.of("deviceId", device.getId())
        );

        if (!sent) {
            throw new IllegalStateException(
                    "Đồng bộ xóa thiết bị qua Kafka thất bại: "
                            + device.getId()
            );
        }
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

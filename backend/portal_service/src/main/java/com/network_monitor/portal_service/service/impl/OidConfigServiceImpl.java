
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
import com.network_monitor.portal_service.service.KafkaService;
import com.network_monitor.portal_service.service.OidConfigService;
import com.network_monitor.portal_service.util.SearchRequestUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OidConfigServiceImpl implements OidConfigService {

    private static final String TOPIC_OID_CONFIG =
            "portal.monitor.oidconfig";

    private final OidConfigRepository oidConfigRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceInterfaceRepository deviceInterfaceRepository;
    private final OidConfigMapper oidConfigMapper;
    private final KafkaService kafkaService;

    @Override
    public ApiResponse<OidConfigResponse> createOidConfig(
            OidConfigRequest request) {
        try {
            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            OidConfig config = OidConfig.builder()
                    .metricScope(request.getMetricScope())
                    .metricType(request.getMetricType())
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

            setInterface(config, device, request.getInterfaceIndex());

            OidConfig saved = oidConfigRepository.saveAndFlush(config);

            sendOidConfigEvent("CREATE", saved);

            return ApiResponse.success(
                    "Tạo OID config thành công",
                    toResponse(saved)
            );
        } catch (Exception e) {
            log.error("Create OID config failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    public ApiResponse<Map<String, Object>> createOidConfigsFromExcel(
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

                if (isEmptyRow(row, formatter, 8)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;

                try {
                    ApiResponse<OidConfigResponse> response =
                            createOidConfig(
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
            log.error("Import OID configs from Excel failed", e);
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
    public ApiResponse<Void> updateOidConfig(
            Long id,
            OidConfigRequest request) {
        try {
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

            OidConfig saved = oidConfigRepository.saveAndFlush(config);

            sendOidConfigEvent("UPDATE", saved);

            return ApiResponse.success(
                    "Cập nhật OID config thành công", null
            );
        } catch (Exception e) {
            log.error("Update OID config failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    public ApiResponse<Void> deleteOidConfig(Long id) {
        try {
            OidConfig config = oidConfigRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy OID config"
                            ));

            sendOidConfigEvent("DELETE", config);
            oidConfigRepository.delete(config);

            return ApiResponse.success(
                    "Xóa OID config thành công", null
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

            for (OidConfig config : configs) {
                sendOidConfigEvent("DELETE", config);
                oidConfigRepository.delete(config);
            }

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

    @Override
    public ApiResponse<OidConfigResponse> getOidConfigById(Long id) {
                return oidConfigRepository.findById(id)
                .map(config -> ApiResponse.success(
                        "Lấy thông tin credential thành công",
                        toResponse(config)
                ))
                .orElseGet(() ->
                        ApiResponse.error("Không tìm thấy credential"));
    }

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
                search, "createdAt"
        );
        String direction = SearchRequestUtils.normalizeDirection(search);

        Pageable pageable = SearchRequestUtils.toPageable(
                page, size, sortBy, direction
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

        long total = oidConfigMapper.countByDeviceId(deviceId, keyword);

        List<OidConfigResponse> responses = configs.stream()
                .map(this::toResponse)
                .toList();

        return new PageImpl<>(responses, pageable, total);
    }

        @Override
        public byte[] exportOidConfigsToExcel() throws IOException {
        List<OidConfig> configs = oidConfigRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                Sheet sheet = workbook.createSheet("OID Configs");

                String[] headers = {
                        "ID",
                        "Metric Scope",
                        "Metric Type",
                        "OID Pattern",
                        "Data Type",
                        "Multiplier",
                        "Device ID",
                        "Interface Index",
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

                for (OidConfig config : configs) {
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(
                        config.getId() != null ? config.getId() : 0L
                );

                row.createCell(1).setCellValue(
                        Objects.toString(config.getMetricScope(), "")
                );

                row.createCell(2).setCellValue(
                        Objects.toString(config.getMetricType(), "")
                );

                row.createCell(3).setCellValue(
                        Objects.toString(config.getOidPattern(), "")
                );

                row.createCell(4).setCellValue(
                        Objects.toString(config.getDataType(), "")
                );

                if (config.getMultiplier() != null) {
                        row.createCell(5).setCellValue(
                                config.getMultiplier().doubleValue()
                        );
                } else {
                        row.createCell(5).setCellValue("");
                }

                row.createCell(6).setCellValue(
                        config.getDevice() != null
                                && config.getDevice().getId() != null
                                ? config.getDevice().getId()
                                : 0L
                );

                if (config.getInterfaceIndex() != null) {
                        row.createCell(7).setCellValue(
                                config.getInterfaceIndex()
                        );
                } else {
                        row.createCell(7).setCellValue("");
                }

                row.createCell(8).setCellValue(
                        Boolean.TRUE.equals(config.getIsActive())
                                ? "Yes"
                                : "No"
                );

                row.createCell(9).setCellValue(
                        Objects.toString(config.getCreatedAt(), "")
                );

                row.createCell(10).setCellValue(
                        Objects.toString(config.getUpdatedAt(), "")
                );
                }

                for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                }

                workbook.write(output);
                return output.toByteArray();
        }
        }

        private OidConfigRequest readRequestFromExcel(
                Row row,
                DataFormatter formatter) {

        String multiplierValue = getCell(row, 4, formatter);
        String deviceIdValue = getCell(row, 5, formatter);
        String interfaceIndexValue = getCell(row, 6, formatter);
        String activeValue = getCell(row, 7, formatter);

        double multiplier = 1.0;
        Long deviceId;
        Integer interfaceIndex = null;
        Boolean isActive;

        // Multiplier: chấp nhận Number hoặc String
        if (!multiplierValue.isBlank()) {
                try {
                multiplier = Double.parseDouble(multiplierValue);
                } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Multiplier phải là số hợp lệ"
                );
                }
        }

        // Device ID: chấp nhận Number hoặc String
        try {
                deviceId = Long.parseLong(deviceIdValue);
        } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Device ID phải là số nguyên hợp lệ"
                );
        }

        // Interface Index: chấp nhận Number hoặc String, có thể để trống
        if (!interfaceIndexValue.isBlank()) {
                try {
                interfaceIndex = Integer.parseInt(interfaceIndexValue);
                } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Interface Index phải là số nguyên hợp lệ"
                );
                }
        }

        // Is Active: Yes/No hoặc 1/0
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

        return OidConfigRequest.builder()
                .metricScope(getCell(row, 0, formatter))
                .metricType(getCell(row, 1, formatter))
                .oidPattern(getCell(row, 2, formatter))
                .dataType(getCell(row, 3, formatter))
                .multiplier(multiplier)
                .deviceId(deviceId)
                .interfaceIndex(interfaceIndex)
                .isActive(isActive)
                .build();
        }

    private void setInterface(
            OidConfig config,
            Device device,
            Integer interfaceIndex) {

        config.setInterfaceIndex(interfaceIndex);

        if (interfaceIndex == null) {
            config.setDeviceInterface(null);
            return;
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

    private void sendOidConfigEvent(
            String eventType,
            OidConfig config) {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", config.getId());
        data.put("deviceId", config.getDevice().getId());
        data.put("metricScope", config.getMetricScope());
        data.put("metricType", config.getMetricType());
        data.put("oidPattern", config.getOidPattern());
        data.put("dataType", config.getDataType());
        data.put("multiplier", config.getMultiplier());
        data.put("interfaceIndex", config.getInterfaceIndex());
        data.put("isActive", config.getIsActive());
        data.put("createdAt", config.getCreatedAt());
        data.put("updatedAt", config.getUpdatedAt());

        boolean sent = kafkaService.sendToMonitor(
                TOPIC_OID_CONFIG,
                eventType,
                String.valueOf(config.getId()),
                data
        );

        if (!sent) {
            throw new IllegalStateException(
                    "Đồng bộ OID config qua Kafka thất bại"
            );
        }
    }

    private OidConfigResponse toResponse(OidConfig config) {
        return OidConfigResponse.builder()
                .id(config.getId())
                .metricScope(config.getMetricScope())
                .metricType(config.getMetricType())
                .oidPattern(config.getOidPattern())
                .dataType(config.getDataType())
                .multiplier(config.getMultiplier())
                .deviceId(config.getDevice().getId())
                .interfaceIndex(config.getInterfaceIndex())
                .isActive(config.getIsActive())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
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

package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.health.Health;
import com.network_monitor.portal_service.mapper.DeviceMapper;
import com.network_monitor.portal_service.model.dto.request.DeviceRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceResponse;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.DeviceService;
import com.network_monitor.portal_service.service.SnmpService;
import com.network_monitor.portal_service.util.SearchRequestUtils;
import com.network_monitor.portal_service.workflow.ApprovalWorkflowService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    private static final String ENTITY_TYPE = "DEVICE";
    private static final String ADMIN_USERNAME = "admin";
    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";
    private static final String IPV4_REGEX =
            "^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}"
                    + "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$";

    private final DeviceRepository deviceRepository;
    private final DeviceMapper deviceMapper;
    private final Health health;
    private final SnmpService snmpService;
    private final ApprovalWorkflowService approvalWorkflowService;

    @Override
    @Transactional
    public ApiResponse<DeviceResponse> createDevice(DeviceRequest request) {
        try {
            validateDeviceRequest(request);

            if (isSuperAdminAccount()) {
                Device saved = insertDeviceDirectly(request);
                return ApiResponse.success(
                        "Thêm thiết bị thành công",
                        toResponse(saved)
                );
            }

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "CREATE",
                    null,
                    null,
                    "Thêm thiết bị mới: " + request.getDeviceName()
                            + " (" + request.getIpAddress() + ")",
                    null,
                    request
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu thêm thiết bị, chờ Checker phê duyệt",
                    null
            );

        } catch (Exception e) {
            log.error("Create device failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Map<String, Object>> createDevicesFromExcel(
            MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ApiResponse.error("File Excel không được để trống");
        }

        boolean bypassApproval = isSuperAdminAccount();

        int totalRows = 0;
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        List<DeviceRequest> validRequests = new ArrayList<>();
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

                    validateDeviceRequest(request);

                    if (bypassApproval) {
                        insertDeviceDirectly(request);
                    } else {
                        validRequests.add(request);
                    }

                    successCount++;

                } catch (Exception e) {
                    failedCount++;

                    addImportError(
                            errors,
                            i + 1,
                            getErrorMessage(e)
                    );
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();

            result.put("totalRows", totalRows);
            result.put("successCount", successCount);
            result.put("failedCount", failedCount);
            result.put("skippedCount", skippedCount);
            result.put("errors", errors);

            if (bypassApproval) {
                return ApiResponse.success(
                        "Import thiết bị từ Excel hoàn tất",
                        result
                );
            }

            if (!validRequests.isEmpty()) {
                ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                        ENTITY_TYPE,
                        "BULK_IMPORT",
                        null,
                        null,
                        "Import " + validRequests.size()
                                + " thiết bị từ file Excel",
                        null,
                        validRequests
                );

                result.put("ticketId", ticket.getId());
                result.put("processInstanceId", ticket.getProcessInstanceId());
            }

            return ApiResponse.success(
                    validRequests.isEmpty()
                            ? "Không có dòng dữ liệu hợp lệ để tạo ticket"
                            : "Đã tạo ticket import Excel, chờ Checker phê duyệt",
                    result
            );

        } catch (Exception e) {
            log.error("Import devices failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
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

    @Override
    @Transactional
    public ApiResponse<Void> updateDevice(
            Long id,
            DeviceRequest request) {

        try {
            Device existing = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            validateDeviceRequest(request);

            if (isSuperAdminAccount()) {
                validateDuplicateDevice(
                        request.getDeviceName().trim(),
                        request.getIpAddress().trim(),
                        id
                );

                existing.setDeviceName(request.getDeviceName().trim());
                existing.setIpAddress(request.getIpAddress().trim());
                existing.setDeviceType(request.getDeviceType().trim());
                existing.setUpdatedAt(OffsetDateTime.now());

                deviceRepository.saveAndFlush(existing);

                return ApiResponse.success(
                        "Cập nhật thiết bị thành công",
                        null
                );
            }

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "UPDATE",
                    existing.getId(),
                    null,
                    "Cập nhật thiết bị ID=" + id
                            + " (" + existing.getDeviceName() + ")",
                    toResponse(existing),
                    request
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu cập nhật thiết bị, chờ Checker phê duyệt",
                    null
            );

        } catch (Exception e) {
            log.error("Update device failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteDevice(Long id) {
        try {
            Device existing = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            if (isSuperAdminAccount()) {
                deviceRepository.delete(existing);
                deviceRepository.flush();

                return ApiResponse.success(
                        "Xóa thiết bị thành công",
                        null
                );
            }

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "DELETE",
                    existing.getId(),
                    null,
                    "Xóa thiết bị ID=" + id
                            + " (" + existing.getDeviceName() + ")",
                    toResponse(existing),
                    null
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu xóa thiết bị, chờ Checker phê duyệt",
                    null
            );

        } catch (Exception e) {
            log.error("Delete device failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteAllDevices() {
        try {
            List<Device> allDevices = deviceRepository.findAll();

            if (allDevices.isEmpty()) {
                return ApiResponse.error("Danh sách thiết bị đang trống");
            }

            if (isSuperAdminAccount()) {
                deviceRepository.deleteAllInBatch();
                deviceRepository.flush();

                return ApiResponse.success(
                        "Đã xóa toàn bộ " + allDevices.size() + " thiết bị",
                        null
                );
            }

            List<DeviceResponse> snapshots = allDevices.stream()
                    .map(this::toResponse)
                    .toList();

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "BULK_DELETE",
                    null,
                    null,
                    "Xóa toàn bộ " + allDevices.size() + " thiết bị",
                    snapshots,
                    null
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu xóa tất cả thiết bị, chờ Checker phê duyệt",
                    null
            );

        } catch (Exception e) {
            log.error("Delete all devices failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    private boolean isSuperAdminAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        String username = null;
        boolean hasSuperAdminRole = false;

        if (auth.getPrincipal() instanceof Jwt jwt) {
            username = jwt.getClaimAsString("preferred_username");
            if (username == null || username.isBlank()) {
                username = jwt.getSubject();
            }

            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if (SUPER_ADMIN_ROLE.equals(String.valueOf(role))) {
                        hasSuperAdminRole = true;
                        break;
                    }
                }
            }
        } else {
            username = auth.getName();
        }

        if (!hasSuperAdminRole && auth.getAuthorities() != null) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if (SUPER_ADMIN_ROLE.equals(authority.getAuthority())) {
                    hasSuperAdminRole = true;
                    break;
                }
            }
        }

        return ADMIN_USERNAME.equalsIgnoreCase(username) && hasSuperAdminRole;
    }

    private Device insertDeviceDirectly(DeviceRequest request) {
        String cleanName = request.getDeviceName().trim();
        String cleanIp = request.getIpAddress().trim();
        String cleanType = request.getDeviceType().trim();

        validateDuplicateDevice(cleanName, cleanIp, null);

        OffsetDateTime now = OffsetDateTime.now();
        String initialStatus = health.checkPing(cleanIp) ? "UP" : "DOWN";

        Device device = Device.builder()
                .deviceName(cleanName)
                .ipAddress(cleanIp)
                .deviceType(cleanType)
                .status(initialStatus)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return deviceRepository.saveAndFlush(device);
    }

    private void validateDuplicateDevice(String deviceName, String ipAddress, Long excludeId) {
        List<Device> existingDevices = deviceRepository.findAll();
        for (Device d : existingDevices) {
            if (excludeId != null && excludeId.equals(d.getId())) {
                continue;
            }
            if (d.getDeviceName() != null && d.getDeviceName().equalsIgnoreCase(deviceName)) {
                throw new IllegalArgumentException("Tên thiết bị đã tồn tại: " + deviceName);
            }
            if (d.getIpAddress() != null && d.getIpAddress().equals(ipAddress)) {
                throw new IllegalArgumentException("Địa chỉ IP đã tồn tại: " + ipAddress);
            }
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

        long offset = (long) page * size;

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

    @Override
    public ApiResponse<DeviceResponse> getDeviceById(Long id) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            return ApiResponse.success(
                    "Lấy thông tin thiết bị thành công",
                    toResponse(device)
            );

        } catch (Exception e) {
            log.error("Get device failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

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
                            "Quy trình phê duyệt",
                            "Dữ liệu hợp lệ sẽ tạo Ticket chờ Checker phê duyệt trước khi lưu vào hệ thống."
                    },
                    {
                            "Status",
                            "Hệ thống tự động kiểm tra ICMP Ping để xác định UP hoặc DOWN sau khi duyệt."
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

    @Override
    public ApiResponse<Map<String, Object>> getDeviceInfo(Long id) {
        try {
            Device device = deviceRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Không tìm thấy thiết bị"));

            Map<String, Object> snmpResult = snmpService.pollDeviceScope(id);

            Map<String, Object> result = new LinkedHashMap<>();

            result.put("id", device.getId());
            result.put("deviceName", device.getDeviceName());
            result.put("ipAddress", device.getIpAddress());
            result.put("deviceType", device.getDeviceType());
            result.put("status", device.getStatus());
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

    private void validateDeviceRequest(DeviceRequest request) {
        if (request == null
                || request.getDeviceName() == null
                || request.getDeviceName().isBlank()
                || request.getIpAddress() == null
                || request.getIpAddress().isBlank()
                || request.getDeviceType() == null
                || request.getDeviceType().isBlank()) {

            throw new IllegalArgumentException(
                    "Device Name, IP Address và Device Type không được để trống"
            );
        }

        if (!request.getIpAddress().matches(IPV4_REGEX)) {
            throw new IllegalArgumentException(
                    "Địa chỉ IPv4 không hợp lệ"
            );
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
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

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
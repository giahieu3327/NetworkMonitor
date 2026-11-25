package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.OidConfigMapper;
import com.network_monitor.portal_service.model.dto.request.OidConfigRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.OidConfigResponse;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.DeviceInterface;
import com.network_monitor.portal_service.model.entity.OidConfig;
import com.network_monitor.portal_service.repository.DeviceInterfaceRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.repository.OidConfigRepository;
import com.network_monitor.portal_service.service.OidConfigService;
import com.network_monitor.portal_service.util.SearchRequestUtils;
import com.network_monitor.portal_service.workflow.ApprovalWorkflowService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OidConfigServiceImpl implements OidConfigService {

    private static final String ENTITY_TYPE = "OID_CONFIG";
    private static final String ADMIN_USERNAME = "admin";
    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";
    private static final String OID_KIND_SINGLE = "SINGLE";
    private static final String OID_KIND_TABLE_COLUMN = "TABLE_COLUMN";
    private static final Set<String> VALID_SCOPES = Set.of("DEVICE", "INTERFACE", "METRIC", "ARP");

    private final OidConfigRepository oidConfigRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceInterfaceRepository deviceInterfaceRepository;
    private final OidConfigMapper oidConfigMapper;
    private final ApprovalWorkflowService approvalWorkflowService;

    @Override
    @Transactional
    public ApiResponse<OidConfigResponse> createOidConfig(
            OidConfigRequest request) {
        try {
            validateCreateRequest(request);

            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            normalizeCreateDefaults(request);
            validateInterfaceExists(device.getId(), request.getInterfaceIndex());

            if (isSuperAdminAccount()) {
                OidConfig saved = insertOidConfigDirectly(device, request);
                return ApiResponse.success(
                        "Thêm OID config thành công",
                        toResponse(saved)
                );
            }

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "CREATE",
                    null,
                    device.getId(),
                    "Thêm OID Config '" + request.getMetricType()
                            + "' (" + request.getMetricScope() + ") cho thiết bị "
                            + device.getDeviceName(),
                    null,
                    request
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu thêm OID config, chờ Checker phê duyệt",
                    null
            );

        } catch (Exception e) {
            log.error("Create OID config failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Map<String, Object>> createOidConfigsFromExcel(
            Long id,
            MultipartFile file) {

        if (id == null) {
            return ApiResponse.error("Device ID không được để trống");
        }

        Device device = deviceRepository.findById(id).orElse(null);
        if (device == null) {
            return ApiResponse.error("Không tìm thấy thiết bị");
        }

        if (file == null || file.isEmpty()) {
            return ApiResponse.error("File Excel không được để trống");
        }

        boolean bypassApproval = isSuperAdminAccount();

        int totalRows = 0;
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        List<OidConfigRequest> validRequests = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        try (InputStream input = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(input)) {

            if (workbook.getNumberOfSheets() == 0) {
                return ApiResponse.error(
                        "File Excel không có sheet dữ liệu"
                );
            }

            Sheet sheet = workbook.getSheet("OID Configs");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

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

                    request.setDeviceId(id);
                    validateCreateRequest(request);
                    normalizeCreateDefaults(request);
                    validateInterfaceExists(id, request.getInterfaceIndex());

                    if (bypassApproval) {
                        insertOidConfigDirectly(device, request);
                    } else {
                        validRequests.add(request);
                    }

                    successCount++;

                } catch (Exception e) {
                    failedCount++;

                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("row", i + 1);
                    error.put("message", getErrorMessage(e));

                    errors.add(error);
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
                        "Import OID config từ Excel hoàn tất",
                        result
                );
            }

            if (!validRequests.isEmpty()) {
                ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                        ENTITY_TYPE,
                        "BULK_IMPORT",
                        null,
                        device.getId(),
                        "Import " + validRequests.size()
                                + " OID Config từ Excel cho thiết bị "
                                + device.getDeviceName(),
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
            log.error(
                    "Import OID configs failed: deviceId={}",
                    id,
                    e
            );

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> updateOidConfig(
            Long id,
            OidConfigRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException(
                        "OID config request không được để trống"
                );
            }

            OidConfig existing = oidConfigRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy OID config"
                            ));

            String effectiveScope = request.getMetricScope() != null
                    ? normalizeMetricScope(request.getMetricScope())
                    : existing.getMetricScope();

            String effectiveKind = request.getOidKind() != null
                    ? normalizeOidKind(request.getOidKind())
                    : existing.getOidKind();

            Double effectiveMultiplier = request.getMultiplier() != null
                    ? request.getMultiplier()
                    : existing.getMultiplier();

            if (effectiveMultiplier == null || !Double.isFinite(effectiveMultiplier) || effectiveMultiplier <= 0) {
                throw new IllegalArgumentException("Multiplier phải lớn hơn 0");
            }

            Device targetDevice = existing.getDevice();
            if (request.getDeviceId() != null) {
                targetDevice = deviceRepository.findById(request.getDeviceId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Không tìm thấy thiết bị"
                                ));
            }

            Integer effectiveInterfaceIndex = request.getInterfaceIndex() != null
                    ? request.getInterfaceIndex()
                    : (request.getDeviceId() != null ? null : existing.getInterfaceIndex());

            validateScopeAndInterface(effectiveScope, effectiveInterfaceIndex);
            validateInterfaceExists(targetDevice.getId(), effectiveInterfaceIndex);

            if (request.getMetricScope() != null) {
                request.setMetricScope(effectiveScope);
            }
            if (request.getOidKind() != null) {
                request.setOidKind(effectiveKind);
            }

            if (isSuperAdminAccount()) {
                existing.setDevice(targetDevice);
                existing.setMetricScope(effectiveScope);
                if (request.getMetricType() != null && !request.getMetricType().isBlank()) {
                    existing.setMetricType(request.getMetricType().trim());
                }
                existing.setOidKind(effectiveKind);
                if (request.getOidPattern() != null && !request.getOidPattern().isBlank()) {
                    existing.setOidPattern(request.getOidPattern().trim());
                }
                if (request.getDataType() != null && !request.getDataType().isBlank()) {
                    existing.setDataType(request.getDataType().trim());
                }
                existing.setMultiplier(effectiveMultiplier);
                existing.setInterfaceIndex(effectiveInterfaceIndex);
                if (request.getIsActive() != null) {
                    existing.setIsActive(request.getIsActive());
                }
                existing.setUpdatedAt(OffsetDateTime.now());

                oidConfigRepository.saveAndFlush(existing);

                return ApiResponse.success(
                        "Cập nhật OID config thành công",
                        null
                );
            }

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "UPDATE",
                    existing.getId(),
                    targetDevice.getId(),
                    "Cập nhật OID Config ID=" + id
                            + " (" + existing.getMetricType() + ")",
                    toResponse(existing),
                    request
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu cập nhật OID config, chờ Checker phê duyệt",
                    null
            );

        } catch (Exception e) {
            log.error("Update OID config failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteOidConfig(Long id) {
        try {
            OidConfig config = oidConfigRepository.findById(id)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy OID config"
                            ));

            if (isSuperAdminAccount()) {
                oidConfigRepository.delete(config);
                oidConfigRepository.flush();

                return ApiResponse.success(
                        "Xóa OID config thành công",
                        null
                );
            }

            Long parentDeviceId = config.getDevice() != null
                    ? config.getDevice().getId()
                    : null;

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "DELETE",
                    config.getId(),
                    parentDeviceId,
                    "Xóa OID Config ID=" + id
                            + " (" + config.getMetricType() + ")",
                    toResponse(config),
                    null
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu xóa OID config, chờ Checker phê duyệt",
                    null
            );

        } catch (Exception e) {
            log.error("Delete OID config failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteOidConfigsByDeviceId(Long deviceId) {
        try {
            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            List<OidConfig> configs =
                    oidConfigRepository.findByDevice_Id(deviceId);

            if (configs.isEmpty()) {
                return ApiResponse.error("Thiết bị không có OID config để xóa");
            }

            if (isSuperAdminAccount()) {
                oidConfigRepository.deleteAllInBatch(configs);
                oidConfigRepository.flush();

                return ApiResponse.success(
                        "Đã xóa toàn bộ " + configs.size()
                                + " OID config của thiết bị " + device.getDeviceName(),
                        null
                );
            }

            List<OidConfigResponse> snapshots = configs.stream()
                    .map(this::toResponse)
                    .toList();

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "BULK_DELETE",
                    null,
                    device.getId(),
                    "Xóa toàn bộ " + configs.size()
                            + " OID Config của thiết bị " + device.getDeviceName(),
                    snapshots,
                    null
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu xóa OID config của thiết bị, chờ Checker phê duyệt",
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

    private OidConfig insertOidConfigDirectly(
            Device device,
            OidConfigRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

        OidConfig config = OidConfig.builder()
                .device(device)
                .metricScope(request.getMetricScope())
                .metricType(request.getMetricType())
                .oidKind(request.getOidKind())
                .oidPattern(request.getOidPattern())
                .dataType(request.getDataType())
                .multiplier(request.getMultiplier())
                .interfaceIndex(request.getInterfaceIndex())
                .isActive(request.getIsActive())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return oidConfigRepository.saveAndFlush(config);
    }

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

    @Override
    public byte[] exportOidConfigs(Long deviceId) throws IOException {

        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException("Không tìm thấy thiết bị");
        }

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

    @Override
    public byte[] downloadOidConfigImportTemplate() throws IOException {

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet dataSheet = workbook.createSheet("OID Configs");

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
                            "Phạm vi metric: DEVICE, INTERFACE, METRIC hoặc ARP"
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
                            "Hệ số nhân dạng số (> 0); để trống sẽ dùng 1.0"
                    },
                    {
                            "Interface Index",
                            "Index interface (> 0) đã tồn tại của thiết bị; "
                                    + "chỉ dùng khi Metric Scope là INTERFACE hoặc METRIC"
                    },
                    {
                            "Is Active",
                            "Yes/No hoặc 1/0; để trống sẽ mặc định Yes"
                    },
                    {
                            "Quy trình phê duyệt",
                            "Dữ liệu hợp lệ sẽ tạo Ticket chờ Checker phê duyệt trước khi lưu vào hệ thống."
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

    private void validateCreateRequest(OidConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "OID config request không được để trống"
            );
        }

        if (request.getDeviceId() == null) {
            throw new IllegalArgumentException(
                    "Device ID không được để trống"
            );
        }

        String scope = normalizeMetricScope(request.getMetricScope());
        normalizeOidKind(request.getOidKind());

        if (request.getMetricType() == null || request.getMetricType().isBlank()) {
            throw new IllegalArgumentException("Metric Type không được để trống");
        }

        if (request.getOidPattern() == null || request.getOidPattern().isBlank()) {
            throw new IllegalArgumentException("OID Pattern không được để trống");
        }

        if (request.getDataType() == null || request.getDataType().isBlank()) {
            throw new IllegalArgumentException("Data Type không được để trống");
        }

        if (request.getMultiplier() != null
                && (!Double.isFinite(request.getMultiplier()) || request.getMultiplier() <= 0)) {
            throw new IllegalArgumentException("Multiplier phải lớn hơn 0");
        }

        validateScopeAndInterface(scope, request.getInterfaceIndex());
    }

    private void normalizeCreateDefaults(OidConfigRequest request) {
        request.setMetricScope(normalizeMetricScope(request.getMetricScope()));
        request.setMetricType(request.getMetricType().trim());
        request.setOidKind(normalizeOidKind(request.getOidKind()));
        request.setOidPattern(request.getOidPattern().trim());
        request.setDataType(request.getDataType().trim());
        if (request.getMultiplier() == null) {
            request.setMultiplier(1.0);
        }
        if (request.getIsActive() == null) {
            request.setIsActive(Boolean.TRUE);
        }
    }

    private void validateScopeAndInterface(String metricScope, Integer interfaceIndex) {
        if (interfaceIndex == null) {
            return;
        }

        if (interfaceIndex <= 0) {
            throw new IllegalArgumentException(
                    "Interface Index phải là số nguyên dương"
            );
        }

        if (!"INTERFACE".equals(metricScope) && !"METRIC".equals(metricScope)) {
            throw new IllegalArgumentException(
                    "Interface Index chỉ được khai báo khi Metric Scope là INTERFACE hoặc METRIC"
            );
        }
    }

    private void validateInterfaceExists(Long deviceId, Integer interfaceIndex) {
        if (interfaceIndex == null) {
            return;
        }

        DeviceInterface.DeviceInterfaceId id =
                new DeviceInterface.DeviceInterfaceId(deviceId, interfaceIndex);

        if (!deviceInterfaceRepository.existsById(id)) {
            throw new IllegalArgumentException(
                    "Không tìm thấy interface " + interfaceIndex
                            + " của thiết bị " + deviceId
            );
        }
    }

    private String normalizeMetricScope(String metricScope) {
        if (metricScope == null || metricScope.isBlank()) {
            throw new IllegalArgumentException(
                    "Metric Scope không được để trống. Chỉ chấp nhận DEVICE, INTERFACE, METRIC hoặc ARP"
            );
        }

        String normalized = metricScope.trim().toUpperCase();
        if (!VALID_SCOPES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Metric Scope không hợp lệ: " + metricScope
                            + ". Chỉ chấp nhận DEVICE, INTERFACE, METRIC hoặc ARP"
            );
        }

        return normalized;
    }

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

        double multiplier = 1.0;

        if (!multiplierValue.isBlank()) {
            try {
                multiplier = Double.parseDouble(multiplierValue);

                if (!Double.isFinite(multiplier) || multiplier <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Multiplier phải là số lớn hơn 0"
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
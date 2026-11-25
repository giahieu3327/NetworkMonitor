package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.DeviceCredentialMapper;
import com.network_monitor.portal_service.model.dto.request.DeviceCredentialRequest;
import com.network_monitor.portal_service.model.dto.request.SearchRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.DeviceCredentialResponse;
import com.network_monitor.portal_service.model.entity.ApprovalTicket;
import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.entity.DeviceCredential;
import com.network_monitor.portal_service.repository.DeviceCredentialRepository;
import com.network_monitor.portal_service.repository.DeviceRepository;
import com.network_monitor.portal_service.service.DeviceCredentialService;
import com.network_monitor.portal_service.util.SearchRequestUtils;
import com.network_monitor.portal_service.workflow.ApprovalWorkflowService;

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
public class DeviceCredentialServiceImpl
        implements DeviceCredentialService {

    private static final String ENTITY_TYPE = "DEVICE_CREDENTIAL";
    private static final String ADMIN_USERNAME = "admin";
    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";
    private static final Set<String> VALID_PROTOCOLS = Set.of("SNMPV1", "SNMPV2C", "SNMPV3");

    private final DeviceCredentialRepository credentialRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCredentialMapper credentialMapper;
    private final ApprovalWorkflowService approvalWorkflowService;

    @Override
    @Transactional
    public ApiResponse<DeviceCredentialResponse> addCredential(
            DeviceCredentialRequest request) {
        try {
            validateCredentialRequest(request, true);

            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            normalizeDefaults(request);

            if (isSuperAdminAccount()) {
                DeviceCredential saved = insertCredentialDirectly(device, request);
                return ApiResponse.success(
                        "Thêm SNMP Credential thành công",
                        toResponse(saved)
                );
            }

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "CREATE",
                    null,
                    device.getId(),
                    "Thêm SNMP Credential '" + request.getCredentialName()
                            + "' cho thiết bị " + device.getDeviceName(),
                    null,
                    request
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu thêm credential, chờ Checker phê duyệt",
                    null
            );
        } catch (Exception e) {
            log.error("Add credential failed", e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Map<String, Object>> createCredentialsFromExcel(
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

        List<DeviceCredentialRequest> validRequests = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        try (InputStream input = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(input)) {

            if (workbook.getNumberOfSheets() == 0) {
                return ApiResponse.error(
                        "File Excel không có sheet dữ liệu"
                );
            }

            Sheet sheet = workbook.getSheet("Credentials");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (isEmptyRow(row, formatter, 6)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;

                try {
                    DeviceCredentialRequest request =
                            readRequestFromExcel(row, formatter);

                    request.setDeviceId(id);
                    validateCredentialRequest(request, true);
                    normalizeDefaults(request);

                    if (bypassApproval) {
                        insertCredentialDirectly(device, request);
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
                        "Import credential từ Excel hoàn tất",
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
                                + " credential từ Excel cho thiết bị "
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
                    "Import credentials failed: deviceId={}",
                    id,
                    e
            );

            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
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

            validateCredentialRequest(request, true);

            Device device = deviceRepository.findById(request.getDeviceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            normalizeDefaults(request);

            if (isSuperAdminAccount()) {
                existing.setDevice(device);
                existing.setCredentialName(request.getCredentialName());
                existing.setCredentialType(request.getCredentialType());
                existing.setProtocolType(request.getProtocolType());
                existing.setCommunityString(
                        request.getCommunityString() != null
                                ? request.getCommunityString().trim()
                                : null
                );
                if (request.getPort() != null) {
                    existing.setPort(request.getPort());
                }
                existing.setIsActive(request.getIsActive());
                existing.setUpdatedAt(OffsetDateTime.now());

                credentialRepository.saveAndFlush(existing);

                return ApiResponse.success(
                        "Cập nhật credential thành công",
                        null
                );
            }

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "UPDATE",
                    existing.getId(),
                    device.getId(),
                    "Cập nhật credential ID=" + id
                            + " (" + existing.getCredentialName() + ")",
                    toResponse(existing),
                    request
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu cập nhật credential, chờ Checker phê duyệt",
                    null
            );
        } catch (Exception e) {
            log.error("Update credential failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteCredential(Long id) {
        try {
            DeviceCredential credential =
                    credentialRepository.findById(id)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Không tìm thấy credential"
                                    ));

            if (isSuperAdminAccount()) {
                credentialRepository.delete(credential);
                credentialRepository.flush();

                return ApiResponse.success(
                        "Xóa credential thành công",
                        null
                );
            }

            Long parentDeviceId = credential.getDevice() != null
                    ? credential.getDevice().getId()
                    : null;

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "DELETE",
                    credential.getId(),
                    parentDeviceId,
                    "Xóa credential ID=" + id
                            + " (" + credential.getCredentialName() + ")",
                    toResponse(credential),
                    null
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu xóa credential, chờ Checker phê duyệt",
                    null
            );
        } catch (Exception e) {
            log.error("Delete credential failed: id={}", id, e);
            return ApiResponse.error(getErrorMessage(e));
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteCredentialsByDeviceId(Long deviceId) {
        try {
            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy thiết bị"
                            ));

            List<DeviceCredential> credentials =
                    credentialRepository.findByDevice_Id(deviceId);

            if (credentials.isEmpty()) {
                return ApiResponse.error("Thiết bị không có credential để xóa");
            }

            if (isSuperAdminAccount()) {
                credentialRepository.deleteAllInBatch(credentials);
                credentialRepository.flush();

                return ApiResponse.success(
                        "Đã xóa toàn bộ " + credentials.size()
                                + " credential của thiết bị " + device.getDeviceName(),
                        null
                );
            }

            List<DeviceCredentialResponse> snapshots = credentials.stream()
                    .map(this::toResponse)
                    .toList();

            ApprovalTicket ticket = approvalWorkflowService.submitTicket(
                    ENTITY_TYPE,
                    "BULK_DELETE",
                    null,
                    device.getId(),
                    "Xóa toàn bộ " + credentials.size()
                            + " credential của thiết bị " + device.getDeviceName(),
                    snapshots,
                    null
            );

            return ApiResponse.success(
                    "Đã tạo ticket #" + ticket.getId()
                            + " yêu cầu xóa credential của thiết bị, chờ Checker phê duyệt",
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

    private DeviceCredential insertCredentialDirectly(
            Device device,
            DeviceCredentialRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

        DeviceCredential credential = DeviceCredential.builder()
                .device(device)
                .credentialName(request.getCredentialName())
                .credentialType(request.getCredentialType())
                .protocolType(request.getProtocolType())
                .communityString(
                        request.getCommunityString() != null
                                ? request.getCommunityString().trim()
                                : null
                )
                .port(request.getPort() != null ? request.getPort() : 161)
                .isActive(request.getIsActive())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return credentialRepository.saveAndFlush(credential);
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
    public byte[] exportCredentials(Long deviceId) throws IOException {

        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new IllegalArgumentException("Không tìm thấy thiết bị");
        }

        List<DeviceCredential> credentials =
                credentialRepository.findByDevice_Id(deviceId);

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

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);

                if (sheet.getColumnWidth(i) > 50 * 256) {
                    sheet.setColumnWidth(i, 50 * 256);
                }
            }

            sheet.createFreezePane(0, 1);

            if (!credentials.isEmpty()) {
                sheet.setAutoFilter(
                        new CellRangeAddress(
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

    @Override
    public byte[] downloadCredentialImportTemplate() throws IOException {

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Credentials");
            Sheet instructions = workbook.createSheet("Instructions");

            String[] headers = {
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

            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            String[][] instructionData = {
                    {"Field", "Description"},
                    {"Credential Name", "Tên credential"},
                    {
                            "Credential Type",
                            "Loại credential, chỉ chấp nhận SNMP"
                    },
                    {
                            "Protocol Type",
                            "Giao thức: SNMPV1, SNMPV2C hoặc SNMPV3"
                    },
                    {
                            "Community String",
                            "SNMP community string hoặc thông tin tương ứng"
                    },
                    {"Port", "Cổng giao tiếp (1 - 65535), ví dụ 161"},
                    {
                            "Is Active",
                            "Chỉ chấp nhận Yes/No hoặc 1/0; để trống mặc định Yes"
                    },
                    {
                            "Quy trình phê duyệt",
                            "Dữ liệu hợp lệ sẽ tạo Ticket chờ Checker phê duyệt trước khi lưu vào hệ thống."
                    },
                    {
                            "Note",
                            "Nhập dữ liệu từ dòng 2 trong sheet Credentials. "
                                    + "Device ID được xác định từ URL import."
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

    private void validateCredentialRequest(
            DeviceCredentialRequest request,
            boolean requireDeviceId) {

        if (request == null) {
            throw new IllegalArgumentException("Credential request không được để trống");
        }

        if (requireDeviceId && request.getDeviceId() == null) {
            throw new IllegalArgumentException("Device ID không được để trống");
        }

        if (request.getCredentialName() == null || request.getCredentialName().isBlank()) {
            throw new IllegalArgumentException("Credential Name không được để trống");
        }

        if (request.getCredentialType() == null
                || !"SNMP".equalsIgnoreCase(request.getCredentialType().trim())) {
            throw new IllegalArgumentException("Credential Type chỉ chấp nhận SNMP");
        }

        if (request.getProtocolType() == null
                || !VALID_PROTOCOLS.contains(request.getProtocolType().trim().toUpperCase())) {
            throw new IllegalArgumentException(
                    "Protocol Type chỉ chấp nhận SNMPV1, SNMPV2C hoặc SNMPV3"
            );
        }

        if (request.getPort() != null
                && (request.getPort() < 1 || request.getPort() > 65535)) {
            throw new IllegalArgumentException("Port phải nằm trong khoảng 1 đến 65535");
        }
    }

    private void normalizeDefaults(DeviceCredentialRequest request) {
        request.setCredentialName(request.getCredentialName().trim());
        request.setCredentialType(request.getCredentialType().trim().toUpperCase());
        request.setProtocolType(request.getProtocolType().trim().toUpperCase());
        if (request.getIsActive() == null) {
            request.setIsActive(Boolean.TRUE);
        }
    }

    private DeviceCredentialRequest readRequestFromExcel(
            Row row,
            DataFormatter formatter) {

        String portValue = getCell(row, 4, formatter);
        String activeValue = getCell(row, 5, formatter);

        Integer port = null;
        Boolean isActive;

        if (!portValue.isBlank()) {
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Port phải là số nguyên hợp lệ"
                );
            }
        }

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

        return DeviceCredentialRequest.builder()
                .credentialName(getCell(row, 0, formatter))
                .credentialType(getCell(row, 1, formatter))
                .protocolType(getCell(row, 2, formatter))
                .communityString(getCell(row, 3, formatter))
                .port(port)
                .isActive(isActive)
                .build();
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
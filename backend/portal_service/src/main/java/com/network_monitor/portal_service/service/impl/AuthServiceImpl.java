
package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.AuthService;
import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.service.MailService;

import lombok.RequiredArgsConstructor;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.keycloak.representations.idm.UserRepresentation;
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
public class AuthServiceImpl implements AuthService {

    private final KeyCloakService keyCloakService;
    private final MailService mailService;
    private final UserRepository userRepository;

    // ========================= REGISTER =========================

    @Override
    public ApiResponse<Void> register(RegisterRequest request) {
        if (request == null) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Thông tin đăng ký không được để trống!"
            );
        }

        if (request.getPassword() == null
                || !request.getPassword().equals(
                        request.getConfirmPassword())) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Mật khẩu xác nhận không khớp!"
            );
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Username đã tồn tại!"
            );
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Email đã tồn tại!"
            );
        }

        ApiResponse<Boolean> keycloakExists =
                keyCloakService.existsByUsername(request.getUsername());

        if (!keycloakExists.isSuccess()) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Không thể kiểm tra username trên Keycloak!"
            );
        }

        if (Boolean.TRUE.equals(keycloakExists.getData())) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Username đã tồn tại trên Keycloak!"
            );
        }

        ApiResponse<String> createResponse =
                keyCloakService.createUser(
                        request.getUsername(),
                        request.getEmail(),
                        request.getFullName()
                );

        if (!createResponse.isSuccess()
                || createResponse.getData() == null) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    createResponse.getErrorDetails()
            );
        }

        String keycloakUserId = createResponse.getData();

        ApiResponse<Void> passwordResponse =
                keyCloakService.setPassword(
                        keycloakUserId,
                        request.getPassword(),
                        false
                );

        if (!passwordResponse.isSuccess()) {
            keyCloakService.deleteUser(keycloakUserId);

            return ApiResponse.error(
                    "Đăng ký thất bại",
                    passwordResponse.getErrorDetails()
            );
        }

        ApiResponse<Void> roleResponse =
                keyCloakService.assignRealmRole(
                        keycloakUserId,
                        request.getRoleName()
                );

        if (!roleResponse.isSuccess()) {
            keyCloakService.deleteUser(keycloakUserId);

            return ApiResponse.error(
                    "Đăng ký thất bại",
                    roleResponse.getErrorDetails()
            );
        }

        ApiResponse<UserRepresentation> keycloakUserResponse =
                keyCloakService.findByUsername(request.getUsername());

        if (!keycloakUserResponse.isSuccess()
                || keycloakUserResponse.getData() == null) {
            keyCloakService.deleteUser(keycloakUserId);

            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Không thể lấy thông tin người dùng từ Keycloak."
            );
        }

        UserRepresentation keycloakUser =
                keycloakUserResponse.getData();

        java.time.OffsetDateTime createdAt;

        if (keycloakUser.getCreatedTimestamp() != null) {
            createdAt = java.time.OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(
                            keycloakUser.getCreatedTimestamp()
                    ),
                    java.time.ZoneOffset.UTC
            );
        } else {
            createdAt = java.time.OffsetDateTime.now(
                    java.time.ZoneOffset.UTC
            );
        }

        User user = User.builder()
                .id(keycloakUserId)
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .roleName(request.getRoleName())
                .isActive(true)
                .emailVerified(false)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();

        try {
            userRepository.save(user);
        } catch (Exception e) {
            keyCloakService.deleteUser(keycloakUserId);

            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Không thể lưu thông tin người dùng vào database!"
            );
        }

        if (Boolean.TRUE.equals(request.getSendEmailVerify())) {
            SendVerificationEmailRequest emailRequest =
                    SendVerificationEmailRequest.builder()
                            .toEmail(request.getEmail())
                            .build();

            ApiResponse<Void> emailResponse =
                    mailService.sendVerificationEmail(emailRequest);

            if (!emailResponse.isSuccess()) {
                return ApiResponse.success(
                        "Đăng ký thành công nhưng không thể gửi email xác thực."
                );
            }
        }

        return ApiResponse.success(
                "Đăng ký tài khoản thành công."
        );
    }

    // ========================= IMPORT USERS =========================

    @Override
    public ApiResponse<Map<String, Object>> importUsersFromExcel(
            MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ApiResponse.error(
                    "File Excel không được để trống"
            );
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

            // Bỏ qua dòng tiêu đề, bắt đầu từ dòng Excel số 2.
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (isEmptyUserRow(row, formatter, 8)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;

                try {
                    String username = getUserCell(row, 0, formatter);
                    String password = getUserCell(row, 1, formatter);
                    String confirmPassword = getUserCell(row, 2, formatter);
                    String email = getUserCell(row, 3, formatter);
                    String fullName = getUserCell(row, 4, formatter);
                    String phoneNumber = getUserCell(row, 5, formatter);
                    String roleName = getUserCell(row, 6, formatter);
                    String sendEmailValue = getUserCell(row, 7, formatter);

                    // Kiểm tra dữ liệu bắt buộc.
                    if (username.isBlank()
                            || password.isBlank()
                            || confirmPassword.isBlank()
                            || email.isBlank()
                            || fullName.isBlank()
                            || roleName.isBlank()) {
                        throw new IllegalArgumentException(
                                "Username, Password, Confirm Password, "
                                        + "Email, Full Name và Role Name "
                                        + "không được để trống"
                        );
                    }

                    if (username.length() > 50) {
                        throw new IllegalArgumentException(
                                "Username không được vượt quá 50 ký tự"
                        );
                    }

                    if (password.length() < 8
                            || password.length() > 100) {
                        throw new IllegalArgumentException(
                                "Password phải từ 8 đến 100 ký tự"
                        );
                    }

                    if (confirmPassword.length() < 8
                            || confirmPassword.length() > 100) {
                        throw new IllegalArgumentException(
                                "Confirm Password phải từ 8 đến 100 ký tự"
                        );
                    }

                    if (email.length() > 100
                            || !email.matches(
                                    "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
                            )) {
                        throw new IllegalArgumentException(
                                "Email không đúng định dạng"
                        );
                    }

                    if (fullName.length() > 100) {
                        throw new IllegalArgumentException(
                                "Full Name không được vượt quá 100 ký tự"
                        );
                    }

                    if (phoneNumber.length() > 20) {
                        throw new IllegalArgumentException(
                                "Phone Number không được vượt quá 20 ký tự"
                        );
                    }

                    if (roleName.length() > 50) {
                        throw new IllegalArgumentException(
                                "Role Name không được vượt quá 50 ký tự"
                        );
                    }

                    boolean sendEmailVerify;

                    if (sendEmailValue.isBlank()
                            || sendEmailValue.equalsIgnoreCase("Yes")
                            || sendEmailValue.equalsIgnoreCase("true")
                            || sendEmailValue.equals("1")) {
                        sendEmailVerify = true;
                    } else if (sendEmailValue.equalsIgnoreCase("No")
                            || sendEmailValue.equalsIgnoreCase("false")
                            || sendEmailValue.equals("0")) {
                        sendEmailVerify = false;
                    } else {
                        throw new IllegalArgumentException(
                                "Send Email Verify chỉ chấp nhận "
                                        + "Yes/No, true/false hoặc 1/0"
                        );
                    }

                    RegisterRequest request = RegisterRequest.builder()
                            .username(username)
                            .password(password)
                            .confirmPassword(confirmPassword)
                            .email(email)
                            .fullName(fullName)
                            .phoneNumber(
                                    phoneNumber.isBlank()
                                            ? null
                                            : phoneNumber
                            )
                            .roleName(roleName)
                            .sendEmailVerify(sendEmailVerify)
                            .build();

                    // Tái sử dụng quy trình đăng ký hiện có.
                    ApiResponse<Void> response = register(request);

                    if (response != null && response.isSuccess()) {
                        successCount++;
                    } else {
                        failedCount++;

                        Map<String, Object> error =
                                new LinkedHashMap<>();

                        error.put("row", i + 1);
                        error.put("username", username);
                        error.put(
                                "message",
                                response == null
                                        ? "Không thể đăng ký tài khoản"
                                        : getRegisterErrorMessage(response)
                        );

                        errors.add(error);
                    }

                } catch (Exception e) {
                    failedCount++;

                    Map<String, Object> error =
                            new LinkedHashMap<>();

                    error.put("row", i + 1);
                    error.put(
                            "username",
                            getUserCell(row, 0, formatter)
                    );
                    error.put(
                            "message",
                            getUserImportErrorMessage(e)
                    );

                    errors.add(error);
                }
            }

        } catch (Exception e) {
            return ApiResponse.error(
                    getUserImportErrorMessage(e)
            );
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

    // ========================= EXPORT USERS =========================

    /**
     * Xuất danh sách User ra file Excel bằng Apache POI.
     */
    @Override
    public byte[] exportUsers() throws IOException {

        List<User> users = userRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Users");

            String[] headers = {
                    "ID",
                    "Username",
                    "Email",
                    "Full Name",
                    "Phone Number",
                    "Role Name",
                    "Is Active",
                    "Email Verified",
                    "Created At",
                    "Updated At"
            };

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            headerStyle.setFillForegroundColor(
                    IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()
            );

            headerStyle.setFillPattern(
                    FillPatternType.SOLID_FOREGROUND
            );

            // Tạo header
            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Ghi dữ liệu User
            int rowIndex = 1;

            for (User user : users) {

                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(
                        user.getId() != null ? user.getId() : ""
                );

                row.createCell(1).setCellValue(
                        user.getUsername() != null
                                ? user.getUsername() : ""
                );

                row.createCell(2).setCellValue(
                        user.getEmail() != null
                                ? user.getEmail() : ""
                );

                row.createCell(3).setCellValue(
                        user.getFullName() != null
                                ? user.getFullName() : ""
                );

                row.createCell(4).setCellValue(
                        user.getPhoneNumber() != null
                                ? user.getPhoneNumber() : ""
                );

                row.createCell(5).setCellValue(
                        user.getRoleName() != null
                                ? user.getRoleName() : ""
                );

                row.createCell(6).setCellValue(
                        Boolean.TRUE.equals(user.getIsActive())
                );

                row.createCell(7).setCellValue(
                        Boolean.TRUE.equals(user.getEmailVerified())
                );

                row.createCell(8).setCellValue(
                        user.getCreatedAt() != null
                                ? user.getCreatedAt().toString() : ""
                );

                row.createCell(9).setCellValue(
                        user.getUpdatedAt() != null
                                ? user.getUpdatedAt().toString() : ""
                );
            }

            // Tự động điều chỉnh độ rộng cột
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);

                if (sheet.getColumnWidth(i) > 50 * 256) {
                    sheet.setColumnWidth(i, 50 * 256);
                }
            }

            // Cố định dòng tiêu đề
            sheet.createFreezePane(0, 1);

            // Bật bộ lọc
            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            0,
                            users.size(),
                            0,
                            headers.length - 1
                    )
            );

            workbook.write(output);

            return output.toByteArray();
        }
    }

    // ========================= DOWNLOAD IMPORT TEMPLATE =========================

    /**
     * Tạo file mẫu Excel để import User.
     *
     * Apache POI được sử dụng để tạo file Excel.
     */
    @Override
    public byte[] downloadUserImportTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            // Sheet dữ liệu import.
            Sheet usersSheet = workbook.createSheet("Users");

            String[] headers = {
                    "Username",
                    "Password",
                    "Confirm Password",
                    "Email",
                    "Full Name",
                    "Phone Number",
                    "Role Name",
                    "Send Email Verify"
            };

            CellStyle headerStyle = workbook.createCellStyle();

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(
                    IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()
            );
            headerStyle.setFillPattern(
                    FillPatternType.SOLID_FOREGROUND
            );

            Row headerRow = usersSheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);

                usersSheet.setColumnWidth(i, 24 * 256);
            }

            usersSheet.createFreezePane(0, 1);
            usersSheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            0, 0, 0, headers.length - 1
                    )
            );

            // Sheet hướng dẫn.
            Sheet instructionsSheet =
                    workbook.createSheet("Instructions");

            String[][] instructions = {
                    {"Column", "Description"},
                    {
                            "Username",
                            "Bắt buộc, tối đa 50 ký tự và không được trùng."
                    },
                    {
                            "Password",
                            "Bắt buộc, từ 8 đến 100 ký tự."
                    },
                    {
                            "Confirm Password",
                            "Bắt buộc và phải giống Password."
                    },
                    {
                            "Email",
                            "Bắt buộc, đúng định dạng email, tối đa 100 ký tự."
                    },
                    {
                            "Full Name",
                            "Bắt buộc, tối đa 100 ký tự."
                    },
                    {
                            "Phone Number",
                            "Không bắt buộc, tối đa 20 ký tự."
                    },
                    {
                            "Role Name",
                            "Bắt buộc, tối đa 50 ký tự; phải là realm role hợp lệ."
                    },
                    {
                            "Send Email Verify",
                            "Để trống, Yes/true/1: gửi email xác minh; "
                                    + "No/false/0: không gửi."
                    }
            };

            CellStyle instructionHeaderStyle =
                    workbook.createCellStyle();

            Font instructionHeaderFont = workbook.createFont();
            instructionHeaderFont.setBold(true);
            instructionHeaderStyle.setFont(instructionHeaderFont);

            for (int i = 0; i < instructions.length; i++) {
                Row row = instructionsSheet.createRow(i);

                for (int j = 0; j < instructions[i].length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(instructions[i][j]);

                    if (i == 0) {
                        cell.setCellStyle(instructionHeaderStyle);
                    }
                }
            }

            instructionsSheet.setColumnWidth(0, 28 * 256);
            instructionsSheet.setColumnWidth(1, 90 * 256);
            instructionsSheet.createFreezePane(0, 1);

            workbook.write(output);

            return output.toByteArray();
        }
    }

    // ========================= LOGIN =========================

    @Override
    public ApiResponse<TokenResponse> login(LoginRequest request) {
        return keyCloakService.login(
                request.getUsername(),
                request.getPassword()
        );
    }

    // ========================= REFRESH TOKEN =========================

    @Override
    public ApiResponse<TokenResponse> refresh(RefreshRequest request) {
        return keyCloakService.refreshToken(
                request.getRefreshToken()
        );
    }

    // ========================= LOGOUT =========================

    @Override
    public ApiResponse<Void> logout(LogoutRequest request) {
        return keyCloakService.logout(
                request.getRefreshToken()
        );
    }

    // ========================= FORGOT PASSWORD =========================

    @Override
    public ApiResponse<Void> forgotPassword(
            ForgotPasswordRequest request) {

        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(request.getEmail());

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {
            return ApiResponse.error(
                    "Không thể thực hiện yêu cầu",
                    "Email không tồn tại trong hệ thống!"
            );
        }

        UserRepresentation user = userResponse.getData();

        SendPasswordResetOtpEmailRequest emailRequest =
                SendPasswordResetOtpEmailRequest.builder()
                        .toEmail(user.getEmail())
                        .build();

        return mailService.sendPasswordResetOtpEmail(emailRequest);
    }

    // ========================= VERIFY RESET PASSWORD OTP =========================

    @Override
    public ApiResponse<String> verifyResetPasswordOtp(
            VerifyResetPasswordOtpRequest request) {

        return mailService.verifyPasswordResetOtp(request);
    }

    // ========================= RESET PASSWORD =========================

    @Override
    public ApiResponse<Void> resetPassword(
            ResetPasswordRequest request) {

        if (!request.getNewPassword().equals(
                request.getConfirmPassword())) {
            return ApiResponse.error(
                    "Đặt lại mật khẩu thất bại",
                    "Mật khẩu xác nhận không khớp!"
            );
        }

        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(request.getEmail());

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {
            return ApiResponse.error(
                    "Đặt lại mật khẩu thất bại",
                    "Email không tồn tại trong hệ thống!"
            );
        }

        String userId = userResponse.getData().getId();

        return keyCloakService.setPassword(
                userId,
                request.getNewPassword(),
                false
        );
    }

    // ========================= SEND VERIFICATION EMAIL =========================

    @Override
    public ApiResponse<Void> sendVerificationEmail(
            SendVerificationEmailRequest request) {

        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(request.getToEmail());

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {
            return ApiResponse.error(
                    "Gửi email xác thực thất bại",
                    "Email không tồn tại trong hệ thống!"
            );
        }

        UserRepresentation user = userResponse.getData();

        if (Boolean.TRUE.equals(user.isEmailVerified())) {
            return ApiResponse.error(
                    "Gửi email xác thực thất bại",
                    "Email đã được xác thực!"
            );
        }

        return mailService.sendVerificationEmail(request);
    }

    // ========================= VERIFY EMAIL =========================

    @Override
    public ApiResponse<String> verifyEmail(
            VerifyEmailRequest request) {

        ApiResponse<String> tokenResponse =
                mailService.verifyEmailToken(request);

        if (!tokenResponse.isSuccess()
                || tokenResponse.getData() == null) {
            return ApiResponse.error(
                    "Xác thực email thất bại",
                    tokenResponse.getErrorDetails()
            );
        }

        String email = tokenResponse.getData();

        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(email);

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {
            return ApiResponse.error(
                    "Xác thực email thất bại",
                    "Không tìm thấy tài khoản!"
            );
        }

        UserRepresentation user = userResponse.getData();
        String userId = user.getId();

        ApiResponse<Void> keycloakResponse =
                keyCloakService.setEmailVerified(userId, true);

        if (!keycloakResponse.isSuccess()) {
            return ApiResponse.error(
                    "Xác thực email thất bại",
                    keycloakResponse.getErrorDetails()
            );
        }

        User localUser =
                userRepository.findById(userId).orElse(null);

        if (localUser == null) {
            return ApiResponse.error(
                    "Xác thực email thất bại",
                    "Không tìm thấy người dùng trong database!"
            );
        }

        localUser.setEmailVerified(true);
        userRepository.save(localUser);

        return ApiResponse.success(
                "Xác thực email thành công.",
                email
        );
    }

    // ========================= PRIVATE HELPERS =========================

    private String getUserCell(
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

    private boolean isEmptyUserRow(
            Row row,
            DataFormatter formatter,
            int columnCount) {

        if (row == null) {
            return true;
        }

        for (int i = 0; i < columnCount; i++) {
            if (!getUserCell(row, i, formatter).isBlank()) {
                return false;
            }
        }

        return true;
    }

    private String getUserImportErrorMessage(Throwable exception) {
        Throwable cause = exception;

        while (cause.getCause() != null
                && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        return cause.getMessage() != null
                ? cause.getMessage()
                : "Đã xảy ra lỗi không xác định";
    }

    private String getRegisterErrorMessage(
            ApiResponse<Void> response) {

        Object errorDetails = response.getErrorDetails();

        if (errorDetails != null
                && !errorDetails.toString().isBlank()) {
            return errorDetails.toString();
        }

        Object message = response.getMessage();

        if (message != null
                && !message.toString().isBlank()) {
            return message.toString();
        }

        return "Đăng ký tài khoản thất bại";
    }
}

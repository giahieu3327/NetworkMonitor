package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.AuthService;
import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.service.MailService;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final KeyCloakService keyCloakService;
    private final MailService mailService;
    private final UserRepository userRepository;

    @Override
    public ApiResponse<Void> register(
            RegisterRequest request
    ) {
        if (!request.getPassword()
                .equals(request.getConfirmPassword())) {

            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Mật khẩu xác nhận không khớp!"
            );
        }

        if (userRepository.existsByUsername(
                request.getUsername()
        )) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Username đã tồn tại!"
            );
        }

        if (userRepository.existsByEmail(
                request.getEmail()
        )) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Email đã tồn tại!"
            );
        }

        ApiResponse<Boolean> keycloakExists =
                keyCloakService.existsByUsername(
                        request.getUsername()
                );

        if (!keycloakExists.isSuccess()) {
            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Không thể kiểm tra username trên Keycloak!"
            );
        }

        if (Boolean.TRUE.equals(
                keycloakExists.getData()
        )) {
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

        String keycloakUserId =
                createResponse.getData();

        ApiResponse<Void> passwordResponse =
                keyCloakService.setPassword(
                        keycloakUserId,
                        request.getPassword(),
                        false
                );

        if (!passwordResponse.isSuccess()) {
            keyCloakService.deleteUser(
                    keycloakUserId
            );

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
            keyCloakService.deleteUser(
                    keycloakUserId
            );

            return ApiResponse.error(
                    "Đăng ký thất bại",
                    roleResponse.getErrorDetails()
            );
        }

        User user =
                User.builder()
                        .id(keycloakUserId)
                        .username(request.getUsername())
                        .email(request.getEmail())
                        .fullName(request.getFullName())
                        .phoneNumber(request.getPhoneNumber())
                        .roleName(request.getRoleName())
                        .isActive(true)
                        .emailVerified(false)
                        .createdAt(LocalDateTime.now())
                        .build();

        try {
            userRepository.save(user);
        } catch (Exception e) {
            keyCloakService.deleteUser(
                    keycloakUserId
            );

            return ApiResponse.error(
                    "Đăng ký thất bại",
                    "Không thể lưu thông tin người dùng vào database!"
            );
        }

        if (Boolean.TRUE.equals(
                request.getSendEmailVerify()
        )) {

            SendVerificationEmailRequest emailRequest =
                    SendVerificationEmailRequest.builder()
                            .toEmail(request.getEmail())
                            .build();

            ApiResponse<Void> emailResponse =
                    mailService.sendVerificationEmail(
                            emailRequest
                    );

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

    @Override
    public ApiResponse<TokenResponse> login(
            LoginRequest request
    ) {
        return keyCloakService.login(
                request.getUsername(),
                request.getPassword()
        );
    }

    @Override
    public ApiResponse<TokenResponse> refresh(
            RefreshRequest request
    ) {
        return keyCloakService.refreshToken(
                request.getRefreshToken()
        );
    }

    @Override
    public ApiResponse<Void> logout(
            LogoutRequest request
    ) {
        return keyCloakService.logout(
                request.getRefreshToken()
        );
    }

    @Override
    public ApiResponse<Void> forgotPassword(
            ForgotPasswordRequest request
    ) {
        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(
                        request.getEmail()
                );

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {

            return ApiResponse.error(
                    "Không thể thực hiện yêu cầu",
                    "Email không tồn tại trong hệ thống!"
            );
        }

        UserRepresentation user =
                userResponse.getData();

        SendPasswordResetOtpEmailRequest emailRequest =
                SendPasswordResetOtpEmailRequest.builder()
                        .toEmail(user.getEmail())
                        .build();

        return mailService.sendPasswordResetOtpEmail(
                emailRequest
        );
    }

    @Override
    public ApiResponse<String> verifyResetPasswordOtp(
            VerifyResetPasswordOtpRequest request
    ) {
        return mailService.verifyPasswordResetOtp(
                request
        );
    }

    @Override
    public ApiResponse<Void> resetPassword(
            ResetPasswordRequest request
    ) {
        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {

            return ApiResponse.error(
                    "Đặt lại mật khẩu thất bại",
                    "Mật khẩu xác nhận không khớp!"
            );
        }

        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(
                        request.getEmail()
                );

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {

            return ApiResponse.error(
                    "Đặt lại mật khẩu thất bại",
                    "Email không tồn tại trong hệ thống!"
            );
        }

        String userId =
                userResponse.getData().getId();

        return keyCloakService.setPassword(
                userId,
                request.getNewPassword(),
                false
        );
    }

    @Override
    public ApiResponse<Void> sendVerificationEmail(
            SendVerificationEmailRequest request
    ) {
        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(
                        request.getToEmail()
                );

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {

            return ApiResponse.error(
                    "Gửi email xác thực thất bại",
                    "Email không tồn tại trong hệ thống!"
            );
        }

        UserRepresentation user =
                userResponse.getData();

        if (Boolean.TRUE.equals(
                user.isEmailVerified()
        )) {
            return ApiResponse.error(
                    "Gửi email xác thực thất bại",
                    "Email đã được xác thực!"
            );
        }

        return mailService.sendVerificationEmail(
                request
        );
    }

    @Override
    public ApiResponse<String> verifyEmail(
            VerifyEmailRequest request
    ) {
        ApiResponse<String> tokenResponse =
                mailService.verifyEmailToken(
                        request
                );

        if (!tokenResponse.isSuccess()
                || tokenResponse.getData() == null) {

            return ApiResponse.error(
                    "Xác thực email thất bại",
                    tokenResponse.getErrorDetails()
            );
        }

        String email =
                tokenResponse.getData();

        ApiResponse<UserRepresentation> userResponse =
                keyCloakService.findByEmail(
                        email
                );

        if (!userResponse.isSuccess()
                || userResponse.getData() == null) {

            return ApiResponse.error(
                    "Xác thực email thất bại",
                    "Không tìm thấy tài khoản!"
            );
        }

        UserRepresentation user =
                userResponse.getData();

        String userId =
                user.getId();

        ApiResponse<Void> keycloakResponse =
                keyCloakService.setEmailVerified(
                        userId,
                        true
                );

        if (!keycloakResponse.isSuccess()) {
            return ApiResponse.error(
                    "Xác thực email thất bại",
                    keycloakResponse.getErrorDetails()
            );
        }

        User localUser =
                userRepository.findById(
                        userId
                ).orElse(null);

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
}
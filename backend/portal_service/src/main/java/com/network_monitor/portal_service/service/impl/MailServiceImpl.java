package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.dto.request.EmailTemplateDataRequest;
import com.network_monitor.portal_service.model.dto.request.SendMailRequest;
import com.network_monitor.portal_service.model.dto.request.SendPasswordResetOtpEmailRequest;
import com.network_monitor.portal_service.model.dto.request.SendVerificationEmailRequest;
import com.network_monitor.portal_service.model.dto.request.VerifyEmailTokenRequest;
import com.network_monitor.portal_service.model.dto.request.VerifyPasswordResetOtpRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.service.MailService;
import com.network_monitor.portal_service.util.OtpUtils;
import com.network_monitor.portal_service.util.TokenUtils;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.model.dto.response.UserResponse;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.KeyCloakService;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    // =========================================================
    // CONSTANT
    // =========================================================

    private static final int EXPIRE_MINUTES = 5;

    private static final long EXPIRE_SECONDS =
            EXPIRE_MINUTES * 60L;

    private static final int TOKEN_BYTE_LENGTH = 32;

    private static final String EMAIL_VERIFY_TOKEN_PREFIX =
            "EMAIL_VERIFY_TOKEN:";

    private static final String PASSWORD_RESET_OTP_PREFIX =
            "PWD_RESET_OTP:";

    private static final String LINK_TEMPLATE =
            "templates/email/link-template.html";

    private static final String OTP_TEMPLATE =
            "templates/email/otp-template.html";


    // =========================================================
    // DEPENDENCY
    // =========================================================

    private final JavaMailSender mailSender;

    private final StringRedisTemplate redisTemplate;

    private final UserRepository userRepository;
    
    private final KeyCloakService keyCloakService;


    // =========================================================
    // CONFIG
    // =========================================================

    @Value("${stalwart.system-sender}")
    private String systemSender;

    @Value("${app.frontend.baseurl}")
    private String frontendBaseUrl;


    // =========================================================
    // 1. SEND TEXT EMAIL
    // =========================================================

    @Override
    public ApiResponse<Void> sendTextEmail(
            SendMailRequest request
    ) {

        try {

            MimeMessage message =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message,
                            false,
                            StandardCharsets.UTF_8.name()
                    );

            helper.setFrom(systemSender);

            helper.setTo(
                    request.getToEmail()
            );

            helper.setSubject(
                    request.getSubject()
            );

            helper.setText(
                    request.getContent(),
                    false
            );

            mailSender.send(message);

            return ApiResponse.success(
                    "Gửi email thành công."
            );

        } catch (Exception e) {

            log.error(
                    "Send text email failed",
                    e
            );

            return ApiResponse.error(
                    "Không thể gửi email."
            );
        }
    }


    // =========================================================
    // 2. SEND HTML EMAIL
    // =========================================================

    @Override
    public ApiResponse<Void> sendHtmlEmail(
            SendMailRequest request
    ) {

        try {

            MimeMessage message =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message,
                            false,
                            StandardCharsets.UTF_8.name()
                    );

            helper.setFrom(systemSender);

            helper.setTo(
                    request.getToEmail()
            );

            helper.setSubject(
                    request.getSubject()
            );

            helper.setText(
                    request.getContent(),
                    true
            );

            mailSender.send(message);

            return ApiResponse.success(
                    "Gửi email HTML thành công."
            );

        } catch (Exception e) {

            log.error(
                    "Send HTML email failed",
                    e
            );

            return ApiResponse.error(
                    "Không thể gửi email HTML."
            );
        }
    }


    // =========================================================
    // 3. SEND EMAIL WITH ATTACHMENT
    // =========================================================

    @Override
    public ApiResponse<Void> sendEmailWithAttachment(
            SendMailRequest request,
            String fileName,
            InputStream fileInputStream
    ) {

        try {

            MimeMessage message =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message,
                            true,
                            StandardCharsets.UTF_8.name()
                    );

            helper.setFrom(systemSender);

            helper.setTo(
                    request.getToEmail()
            );

            helper.setSubject(
                    request.getSubject()
            );

            helper.setText(
                    request.getContent(),
                    false
            );

            helper.addAttachment(
                    fileName,
                    fileInputStream
            );

            mailSender.send(message);

            return ApiResponse.success(
                    "Gửi email kèm file thành công."
            );

        } catch (Exception e) {

            log.error(
                    "Send email with attachment failed",
                    e
            );

            return ApiResponse.error(
                    "Không thể gửi email kèm file."
            );
        }
    }


    // =========================================================
    // 4. SEND VERIFICATION EMAIL
    // =========================================================

    @Override
    public ApiResponse<Void> sendVerificationEmail(
            SendVerificationEmailRequest request
    ) {

        try {

            if (request == null) {

                return ApiResponse.error(
                        "Request không hợp lệ."
                );
            }

            String toEmail =
                    request.getToEmail();

            String userName =
                    request.getUserName();

            if (
                    toEmail == null
                            || toEmail.isBlank()
            ) {

                return ApiResponse.error(
                        "Email không được để trống."
                );
            }


            // -------------------------------------------------
            // Generate UNIQUE TOKEN
            // -------------------------------------------------

            String token =
                    generateUniqueVerificationToken();


            // -------------------------------------------------
            // Redis
            //
            // EMAIL_VERIFY_TOKEN:{token}
            //              ↓
            //           email
            //              ↓
            //          TTL 5 phút
            // -------------------------------------------------

            String redisKey =
                    EMAIL_VERIFY_TOKEN_PREFIX + token;

            redisTemplate.opsForValue().set(
                    redisKey,
                    toEmail,
                    EXPIRE_SECONDS,
                    TimeUnit.SECONDS
            );


            // -------------------------------------------------
            // Verification link
            // -------------------------------------------------

            String verificationLink =
                    frontendBaseUrl
                            + "/verify-email?token="
                            + token;


            // -------------------------------------------------
            // Template DTO
            // -------------------------------------------------

            EmailTemplateDataRequest templateData =
                    EmailTemplateDataRequest.builder()
                            .title("Xác thực email")
                            .userName(
                                    userName != null
                                            ? userName
                                            : "User"
                            )
                            .message(
                                    "Vui lòng nhấn nút bên dưới "
                                            + "để xác thực địa chỉ email "
                                            + "của bạn."
                            )
                            .verificationLink(
                                    verificationLink
                            )
                            .buttonText(
                                    "Xác thực email"
                            )
                            .expireMinutes(
                                    EXPIRE_MINUTES
                            )
                            .build();


            // -------------------------------------------------
            // Render template
            // -------------------------------------------------

            String html =
                    renderTemplate(
                            LINK_TEMPLATE,
                            templateData
                    );


            // -------------------------------------------------
            // Send
            // -------------------------------------------------

            SendMailRequest mailRequest =
                    SendMailRequest.builder()
                            .toEmail(toEmail)
                            .subject("Xác thực email")
                            .content(html)
                            .build();

            return sendHtmlEmail(
                    mailRequest
            );

        } catch (Exception e) {

            log.error(
                    "Send verification email failed",
                    e
            );

            return ApiResponse.error(
                    "Không thể gửi email xác thực."
            );
        }
    }

        // =========================================================
        // 5. VERIFY EMAIL TOKEN
        // =========================================================

        @Override
        @Transactional
        public ApiResponse<String> verifyEmailToken(
                VerifyEmailTokenRequest request
        ) {

        try {

                // -------------------------------------------------
                // Validate request
                // -------------------------------------------------

                if (
                        request == null
                                || request.getToken() == null
                                || request.getToken().isBlank()
                ) {

                return ApiResponse.error(
                        "Token không hợp lệ."
                );
                }

                String token = request.getToken();

                String redisKey =
                        EMAIL_VERIFY_TOKEN_PREFIX + token;


                // -------------------------------------------------
                // Find email by token
                // -------------------------------------------------

                String email =
                        redisTemplate.opsForValue()
                                .get(redisKey);


                // -------------------------------------------------
                // Token not found / expired
                // -------------------------------------------------

                if (
                        email == null
                                || email.isBlank()
                ) {

                return ApiResponse.error(
                        "Token không tồn tại hoặc đã hết hạn."
                );
                }


                // -------------------------------------------------
                // Find user in Keycloak
                // -------------------------------------------------

                ApiResponse<UserRepresentation> keycloakResponse =
                        keyCloakService.findByEmail(email);

                if (
                        keycloakResponse == null
                                || !keycloakResponse.isSuccess()
                                || keycloakResponse.getData() == null
                ) {

                return ApiResponse.error(
                        "Không tìm thấy tài khoản."
                );
                }

                UserRepresentation keycloakUser =
                        keycloakResponse.getData();

                String userId =
                        keycloakUser.getId();


                // -------------------------------------------------
                // Update emailVerified in Keycloak
                // -------------------------------------------------

                keycloakUser.setEmailVerified(true);

                keyCloakService
                        .getUsersResource()
                        .get(userId)
                        .update(keycloakUser);


                // -------------------------------------------------
                // Update emailVerified in PostgreSQL
                // -------------------------------------------------

                Optional<User> userOptional =
                        userRepository.findById(userId);

                if (userOptional.isEmpty()) {

                return ApiResponse.error(
                        "Không tìm thấy người dùng trong hệ thống."
                );
                }

                User user =
                        userOptional.get();

                user.setEmailVerified(true);

                userRepository.save(user);


                // -------------------------------------------------
                // Delete token
                // Prevent reuse
                // -------------------------------------------------

                redisTemplate.delete(redisKey);


                // -------------------------------------------------
                // Success
                // -------------------------------------------------

                return ApiResponse.success(
                        "Xác thực email thành công.",
                        email
                );

        } catch (Exception e) {

                log.error(
                        "Verify email token failed",
                        e
                );

                return ApiResponse.error(
                        "Không thể xác thực email."
                );
        }
        }



    // =========================================================
    // 6. SEND PASSWORD RESET OTP
    // =========================================================

    @Override
    public ApiResponse<Void> sendPasswordResetOtpEmail(
            SendPasswordResetOtpEmailRequest request
    ) {

        try {

            if (request == null) {

                return ApiResponse.error(
                        "Request không hợp lệ."
                );
            }

            String toEmail =
                    request.getToEmail();

            String userName =
                    request.getUserName();

            if (
                    toEmail == null
                            || toEmail.isBlank()
            ) {

                return ApiResponse.error(
                        "Email không được để trống."
                );
            }


            // -------------------------------------------------
            // Generate UNIQUE OTP
            // -------------------------------------------------

            String otp =
                    generateUniquePasswordResetOtp();


            // -------------------------------------------------
            // Redis
            //
            // PWD_RESET_OTP:{otp}
            //             ↓
            //          email
            //             ↓
            //         TTL 5 phút
            // -------------------------------------------------

            String redisKey =
                    PASSWORD_RESET_OTP_PREFIX + otp;

            redisTemplate.opsForValue().set(
                    redisKey,
                    toEmail,
                    EXPIRE_SECONDS,
                    TimeUnit.SECONDS
            );


            // -------------------------------------------------
            // Template DTO
            // -------------------------------------------------

            EmailTemplateDataRequest templateData =
                    EmailTemplateDataRequest.builder()
                            .title("Mã xác thực")
                            .userName(
                                    userName != null
                                            ? userName
                                            : "User"
                            )
                            .message(
                                    "Vui lòng sử dụng mã bên dưới "
                                            + "để xác nhận yêu cầu "
                                            + "đặt lại mật khẩu."
                            )
                            .otp(otp)
                            .expireMinutes(
                                    EXPIRE_MINUTES
                            )
                            .build();


            // -------------------------------------------------
            // Render template
            // -------------------------------------------------

            String html =
                    renderTemplate(
                            OTP_TEMPLATE,
                            templateData
                    );


            // -------------------------------------------------
            // Send email
            // -------------------------------------------------

            SendMailRequest mailRequest =
                    SendMailRequest.builder()
                            .toEmail(toEmail)
                            .subject("Mã xác thực")
                            .content(html)
                            .build();

            return sendHtmlEmail(
                    mailRequest
            );

        } catch (Exception e) {

            log.error(
                    "Send password reset OTP failed",
                    e
            );

            return ApiResponse.error(
                    "Không thể gửi OTP."
            );
        }
    }


    // =========================================================
    // 7. VERIFY PASSWORD RESET OTP
    // =========================================================

    @Override
    public ApiResponse<String> verifyPasswordResetOtp(
            VerifyPasswordResetOtpRequest request
    ) {

        try {

            if (request == null) {

                return ApiResponse.error(
                        "Request không hợp lệ."
                );
            }

            String otp =
                    request.getOtp();

            if (
                    otp == null
                            || otp.isBlank()
            ) {

                return ApiResponse.error(
                        "OTP không được để trống."
                );
            }


            // -------------------------------------------------
            // Find email by OTP
            //
            // PWD_RESET_OTP:{otp}
            //          ↓
            //        email
            // -------------------------------------------------

            String redisKey =
                    PASSWORD_RESET_OTP_PREFIX + otp;

            String email =
                    redisTemplate.opsForValue()
                            .get(redisKey);


            // -------------------------------------------------
            // OTP not found / expired
            // -------------------------------------------------

            if (
                    email == null
                            || email.isBlank()
            ) {

                return ApiResponse.error(
                        "OTP không tồn tại hoặc đã hết hạn."
                );
            }


            // -------------------------------------------------
            // Delete OTP
            // Prevent reuse
            // -------------------------------------------------

            redisTemplate.delete(
                    redisKey
            );


            return ApiResponse.success(
                    "Xác thực OTP thành công.",
                    email
            );

        } catch (Exception e) {

            log.error(
                    "Verify password reset OTP failed",
                    e
            );

            return ApiResponse.error(
                    "Không thể xác thực OTP."
            );
        }
    }


    // =========================================================
    // GENERATE UNIQUE VERIFICATION TOKEN
    // =========================================================

    private String generateUniqueVerificationToken() {

        String token;

        do {

            token =
                    TokenUtils.generateRandomToken(
                            TOKEN_BYTE_LENGTH
                    );

        } while (
                redisTemplate.hasKey(
                        EMAIL_VERIFY_TOKEN_PREFIX + token
                )
        );

        return token;
    }


    // =========================================================
    // GENERATE UNIQUE PASSWORD RESET OTP
    // =========================================================

    private String generateUniquePasswordResetOtp() {

        String otp;

        do {

            otp =
                    OtpUtils.generateOtp();

        } while (
                redisTemplate.hasKey(
                        PASSWORD_RESET_OTP_PREFIX + otp
                )
        );

        return otp;
    }


    // =========================================================
    // RENDER TEMPLATE
    // =========================================================

    private String renderTemplate(
            String templatePath,
            EmailTemplateDataRequest data
    ) {

        try {

            ClassPathResource resource =
                    new ClassPathResource(
                            templatePath
                    );

            if (!resource.exists()) {

                throw new IllegalArgumentException(
                        "Không tìm thấy email template: "
                                + templatePath
                );
            }

            String template;

            try (
                    InputStream inputStream =
                            resource.getInputStream()
            ) {

                template =
                        new String(
                                inputStream.readAllBytes(),
                                StandardCharsets.UTF_8
                        );
            }


            // -------------------------------------------------
            // Replace placeholders
            // -------------------------------------------------

            template =
                    template.replace(
                            "{{title}}",
                            safe(data.getTitle())
                    );

            template =
                    template.replace(
                            "{{userName}}",
                            safe(data.getUserName())
                    );

            template =
                    template.replace(
                            "{{message}}",
                            safe(data.getMessage())
                    );

            template =
                    template.replace(
                            "{{otp}}",
                            safe(data.getOtp())
                    );

            template =
                    template.replace(
                            "{{verificationLink}}",
                            safe(data.getVerificationLink())
                    );

            template =
                    template.replace(
                            "{{buttonText}}",
                            safe(data.getButtonText())
                    );

            template =
                    template.replace(
                            "{{expireMinutes}}",
                            String.valueOf(
                                    data.getExpireMinutes()
                            )
                    );

            return template;

        } catch (Exception e) {

            log.error(
                    "Render email template failed: {}",
                    templatePath,
                    e
            );

            throw new RuntimeException(
                    "Không thể render email template.",
                    e
            );
        }
    }


    // =========================================================
    // SAFE STRING
    // =========================================================

    private String safe(String value) {

        return value != null
                ? value
                : "";
    }
}
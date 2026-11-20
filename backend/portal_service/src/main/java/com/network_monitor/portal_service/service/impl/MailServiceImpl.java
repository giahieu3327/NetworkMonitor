package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.*;
import com.network_monitor.portal_service.model.dto.response.*;
import com.network_monitor.portal_service.service.MailService;
import com.network_monitor.portal_service.util.EmailTemplateUtils;
import com.network_monitor.portal_service.util.JwtTokenUtils;
import com.network_monitor.portal_service.util.OtpUtils;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private static final long EMAIL_VERIFY_EXPIRE_MINUTES = 1440;
    private static final long PASSWORD_RESET_OTP_EXPIRE_MINUTES = 5;

    private static final String PASSWORD_RESET_OTP_PREFIX = "PWD_RESET_OTP:";

    private static final String EMAIL_VERIFY_TEMPLATE =
            "templates/email/link-email.html";

    private static final String PASSWORD_RESET_OTP_TEMPLATE =
            "templates/email/otp-email.html";

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Value("${stalwart.system-sender}")
    private String systemSender;

    @Value("${app.frontend.baseurl}")
    private String frontendBaseUrl;

    @Value("${app.jwt.email-verification-secret}")
    private String emailVerificationSecret;

    @Override
    public ApiResponse<Void> sendTextEmail(SendMailRequest request) {
        try {
            if (request == null
                    || request.getToEmail() == null
                    || request.getToEmail().isBlank()) {
                return ApiResponse.error("Email người nhận không hợp lệ.");
            }

            if (request.getSubject() == null
                    || request.getSubject().isBlank()) {
                return ApiResponse.error("Tiêu đề email không được để trống.");
            }

            if (request.getContent() == null) {
                return ApiResponse.error("Nội dung email không được để trống.");
            }

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(systemSender);
            helper.setTo(request.getToEmail());
            helper.setSubject(request.getSubject());
            helper.setText(request.getContent(), false);

            mailSender.send(message);

            return ApiResponse.success("Gửi email thành công.");

        } catch (Exception e) {
            return ApiResponse.error(
                    "Không thể gửi email: " + e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<Void> sendHtmlEmail(SendMailRequest request) {
        try {
            if (request == null
                    || request.getToEmail() == null
                    || request.getToEmail().isBlank()) {
                return ApiResponse.error("Email người nhận không hợp lệ.");
            }

            if (request.getSubject() == null
                    || request.getSubject().isBlank()) {
                return ApiResponse.error("Tiêu đề email không được để trống.");
            }

            if (request.getContent() == null) {
                return ApiResponse.error("Nội dung email không được để trống.");
            }

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(systemSender);
            helper.setTo(request.getToEmail());
            helper.setSubject(request.getSubject());
            helper.setText(request.getContent(), true);

            mailSender.send(message);

            return ApiResponse.success("Gửi email thành công.");

        } catch (Exception e) {
            return ApiResponse.error(
                    "Không thể gửi email: " + e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<Void> sendEmailWithAttachment(
            SendMailRequest request,
            String fileName,
            InputStream fileInputStream
    ) {
        try {
            if (request == null
                    || request.getToEmail() == null
                    || request.getToEmail().isBlank()) {
                return ApiResponse.error("Email người nhận không hợp lệ.");
            }

            if (request.getSubject() == null
                    || request.getSubject().isBlank()) {
                return ApiResponse.error("Tiêu đề email không được để trống.");
            }

            if (request.getContent() == null) {
                return ApiResponse.error("Nội dung email không được để trống.");
            }

            if (fileName == null || fileName.isBlank()) {
                return ApiResponse.error("Tên file không hợp lệ.");
            }

            if (fileInputStream == null) {
                return ApiResponse.error("File đính kèm không hợp lệ.");
            }

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(systemSender);
            helper.setTo(request.getToEmail());
            helper.setSubject(request.getSubject());
            helper.setText(request.getContent(), true);
            helper.addAttachment(
                    fileName,
                    (InputStreamSource) () -> fileInputStream
            );

            mailSender.send(message);

            return ApiResponse.success("Gửi email thành công.");

        } catch (Exception e) {
            return ApiResponse.error(
                    "Không thể gửi email: " + e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<Void> sendVerificationEmail(
            SendVerificationEmailRequest request
    ) {
        try {
            if (request == null
                    || request.getToEmail() == null
                    || request.getToEmail().isBlank()) {
                return ApiResponse.error("Email không hợp lệ.");
            }

            String email = request.getToEmail();

            String token = JwtTokenUtils.generateToken(
                    email,
                    emailVerificationSecret,
                    EMAIL_VERIFY_EXPIRE_MINUTES
            );

            String verificationLink =
                    frontendBaseUrl
                            + "/verify-email?token="
                            + token;

            EmailTemplateDataRequest templateData =
                    EmailTemplateDataRequest.builder()
                            .title("Xác thực email")
                            .message(
                                    "Vui lòng nhấn vào nút bên dưới để xác thực địa chỉ email của bạn."
                            )
                            .link(verificationLink)
                            .buttonText("Xác thực email")
                            .expireMessage(
                                    "Liên kết xác thực có hiệu lực trong 24 giờ."
                            )
                            .footerMessage(
                                    "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email."
                            )
                            .build();

            String html =
                    EmailTemplateUtils.render(
                            EMAIL_VERIFY_TEMPLATE,
                            templateData
                    );

            SendMailRequest mailRequest =
                    SendMailRequest.builder()
                            .toEmail(email)
                            .subject("Xác thực email")
                            .content(html)
                            .build();

            return sendHtmlEmail(mailRequest);

        } catch (Exception e) {
            return ApiResponse.error(
                    "Không thể gửi email xác thực: " + e.getMessage()
            );
        }
    }

    @Override
    public ApiResponse<String> verifyEmailToken(
            VerifyEmailRequest request
    ) {
        try {
            if (request == null
                    || request.getToken() == null
                    || request.getToken().isBlank()) {
                return ApiResponse.error(
                        "Token xác thực không hợp lệ."
                );
            }

            String token = request.getToken();

            if (!JwtTokenUtils.isValid(
                    token,
                    emailVerificationSecret
            )) {
                return ApiResponse.error(
                        "Token xác thực không hợp lệ hoặc đã hết hạn."
                );
            }

            String email =
                    JwtTokenUtils.getEmail(
                            token,
                            emailVerificationSecret
                    );

            if (email == null || email.isBlank()) {
                return ApiResponse.error(
                        "Token không chứa email hợp lệ."
                );
            }

            return ApiResponse.success(
                    "Token xác thực hợp lệ.",
                    email
            );

        } catch (Exception e) {
            return ApiResponse.error(
                    "Không thể xác thực token."
            );
        }
    }

        @Override
        public ApiResponse<Void> sendPasswordResetOtpEmail(
                SendPasswordResetOtpEmailRequest request
        ) {
        try {
                if (request == null
                        || request.getToEmail() == null
                        || request.getToEmail().isBlank()) {
                return ApiResponse.error("Email không hợp lệ.");
                }

                String email = request.getToEmail();
                String redisKey = PASSWORD_RESET_OTP_PREFIX + email;

                String existingOtp =
                        redisTemplate.opsForValue().get(redisKey);

                if (existingOtp != null && !existingOtp.isBlank()) {
                return ApiResponse.error(
                        "OTP hiện tại vẫn còn hiệu lực."
                );
                }

                String otp = OtpUtils.generateOtp();

                redisTemplate.opsForValue().set(
                        redisKey,
                        otp,
                        PASSWORD_RESET_OTP_EXPIRE_MINUTES,
                        TimeUnit.MINUTES
                );

                EmailTemplateDataRequest templateData =
                        EmailTemplateDataRequest.builder()
                                .title("Đặt lại mật khẩu")
                                .message(
                                        "Sử dụng mã OTP bên dưới để tiếp tục đặt lại mật khẩu."
                                )
                                .otp(otp)
                                .expireMessage(
                                        "Mã OTP có hiệu lực trong 5 phút."
                                )
                                .footerMessage(
                                        "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email."
                                )
                                .build();

                String html =
                        EmailTemplateUtils.render(
                                PASSWORD_RESET_OTP_TEMPLATE,
                                templateData
                        );

                SendMailRequest mailRequest =
                        SendMailRequest.builder()
                                .toEmail(email)
                                .subject("Mã OTP đặt lại mật khẩu")
                                .content(html)
                                .build();

                ApiResponse<Void> response =
                        sendHtmlEmail(mailRequest);

                if (!response.isSuccess()) {
                redisTemplate.delete(redisKey);
                }

                return response;

        } catch (Exception e) {
                return ApiResponse.error(
                        "Không thể gửi OTP: " + e.getMessage()
                );
        }
        }


    @Override
    public ApiResponse<String> verifyPasswordResetOtp(
            VerifyResetPasswordOtpRequest request
    ) {
        try {
            if (request == null
                    || request.getEmail() == null
                    || request.getEmail().isBlank()) {
                return ApiResponse.error(
                        "Email không hợp lệ."
                );
            }

            if (request.getOtp() == null
                    || request.getOtp().isBlank()) {
                return ApiResponse.error(
                        "OTP không hợp lệ."
                );
            }

            String redisKey =
                    PASSWORD_RESET_OTP_PREFIX
                            + request.getEmail();

            String storedOtp =
                    redisTemplate.opsForValue().get(redisKey);

            if (storedOtp == null) {
                return ApiResponse.error(
                        "OTP không tồn tại hoặc đã hết hạn."
                );
            }

            if (!storedOtp.equals(request.getOtp())) {
                return ApiResponse.error(
                        "OTP không chính xác."
                );
            }

            redisTemplate.delete(redisKey);

            return ApiResponse.success(
                    "OTP hợp lệ.",
                    request.getEmail()
            );

        } catch (Exception e) {
            return ApiResponse.error(
                    "Không thể xác thực OTP."
            );
        }
    }
}
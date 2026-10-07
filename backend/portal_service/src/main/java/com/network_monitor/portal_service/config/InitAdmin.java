package com.network_monitor.portal_service.config;

import com.network_monitor.portal_service.model.dto.request.SendVerificationEmailRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.UserResponse;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class InitAdmin implements CommandLineRunner {

    private final KeyCloakService keyCloakService;
    private final MailService mailService;
    private final UserRepository userRepository;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.full-name}")
    private String adminFullName;

    @Value("${app.admin.phone}")
    private String adminPhone;

    @Value("${app.admin.role}")
    private String adminRole;

    @Override
    public void run(String... args) {
        try {
            initAdminAccount();
        } catch (Exception e) {
            log.error(
                    "Lỗi trong quá trình khởi tạo tài khoản Super Admin mặc định: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    private void initAdminAccount() {
        boolean keycloakExists = checkKeycloakAdminExists();
        boolean postgresExists = checkPostgresAdminExists();

        if (keycloakExists && postgresExists) {
            log.info(
                    "Tài khoản Super Admin đã tồn tại đầy đủ trên cả Keycloak và PostgreSQL. Bỏ qua khởi tạo."
            );
            return;
        }

        String keycloakUserId = syncKeycloakAdmin(keycloakExists);

        syncPostgresAdmin(postgresExists, keycloakUserId);

        sendAdminVerificationEmail();
    }

    private boolean checkKeycloakAdminExists() {
        ApiResponse<Boolean> response =
                keyCloakService.existsByUsername(adminUsername);

        return response.isSuccess()
                && Boolean.TRUE.equals(response.getData());
    }

    private boolean checkPostgresAdminExists() {
        return userRepository
                .findByUsername(adminUsername)
                .isPresent();
    }

    private String syncKeycloakAdmin(boolean exists) {
        if (exists) {
            log.info(
                    "Tài khoản Super Admin đã tồn tại trên Keycloak. Lấy Keycloak ID..."
            );

            ApiResponse<UserRepresentation> response =
                    keyCloakService.findByUsername(adminUsername);

            if (response.isSuccess()
                    && response.getData() != null
                    && response.getData().getId() != null) {

                return response.getData().getId();
            }

            throw new IllegalStateException(
                    "Không tìm thấy UUID Keycloak cho user "
                            + adminUsername
            );
        }

        log.info(
                "Tạo tài khoản Super Admin mới trên Keycloak."
        );

        ApiResponse<String> createResponse =
                keyCloakService.createUser(
                        adminUsername,
                        adminEmail,
                        adminFullName
                );

        if (!createResponse.isSuccess()
                || createResponse.getData() == null) {

            throw new IllegalStateException(
                    "Không thể tạo Super Admin trên Keycloak: "
                            + createResponse.getErrorDetails()
            );
        }

        String keycloakUserId = createResponse.getData();

        ApiResponse<Void> passwordResponse =
                keyCloakService.setPassword(
                        keycloakUserId,
                        adminPassword,
                        false
                );

        if (!passwordResponse.isSuccess()) {
            throw new IllegalStateException(
                    "Không thể cấu hình mật khẩu Super Admin trên Keycloak: "
                            + passwordResponse.getErrorDetails()
            );
        }

        ApiResponse<Void> roleResponse =
                keyCloakService.assignRealmRole(
                        keycloakUserId,
                        adminRole
                );

        if (!roleResponse.isSuccess()) {
            throw new IllegalStateException(
                    "Không thể gán role Super Admin trên Keycloak: "
                            + roleResponse.getErrorDetails()
            );
        }

        return keycloakUserId;
    }

    private void sendAdminVerificationEmail() {
        SendVerificationEmailRequest request =
                new SendVerificationEmailRequest();

        request.setToEmail(adminEmail);

        ApiResponse<Void> response =
                mailService.sendVerificationEmail(request);

        if (!response.isSuccess()) {
            throw new IllegalStateException(
                    "Không thể gửi email xác thực Super Admin: "
                            + response.getErrorDetails()
            );
        }

        log.info(
                "Đã gửi email xác thực Super Admin tới {}",
                adminEmail
        );
    }

    private void syncPostgresAdmin(
            boolean exists,
            String keycloakUserId
    ) {
        if (exists) {
            log.info(
                    "Tài khoản Super Admin đã tồn tại trong PostgreSQL."
            );
            return;
        }

        log.info(
                "Đồng bộ Super Admin từ Keycloak sang PostgreSQL. Keycloak UUID: {}",
                keycloakUserId
        );

        ApiResponse<UserRepresentation> keycloakUserResponse =
                keyCloakService.findByUsername(adminUsername);

        if (!keycloakUserResponse.isSuccess()
                || keycloakUserResponse.getData() == null) {

            throw new IllegalStateException(
                    "Không thể lấy thông tin Super Admin từ Keycloak."
            );
        }

        UserRepresentation keycloakUser =
                keycloakUserResponse.getData();

        LocalDateTime createdAt;

        if (keycloakUser.getCreatedTimestamp() != null) {
        createdAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(keycloakUser.getCreatedTimestamp()),
                ZoneOffset.UTC
        );
        }
        else {
            createdAt =
                    LocalDateTime.now(ZoneOffset.UTC);
        }

        boolean emailVerified =
                Boolean.TRUE.equals(
                        keycloakUser.isEmailVerified()
                );

        User adminUser =
                User.builder()
                        .id(keycloakUserId)
                        .username(adminUsername)
                        .email(adminEmail)
                        .fullName(adminFullName)
                        .phoneNumber(adminPhone)
                        .roleName(adminRole)
                        .isActive(
                                Boolean.TRUE.equals(
                                        keycloakUser.isEnabled()
                                )
                        )
                        .emailVerified(emailVerified)
                        .createdAt(createdAt)
                        .build();

        userRepository.save(adminUser);

        log.info(
                "Khởi tạo tài khoản Super Admin mặc định hoàn tất. username={}, emailVerified={}",
                adminUsername,
                emailVerified
        );
    }
}
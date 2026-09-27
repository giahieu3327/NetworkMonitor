package com.network_monitor.portal_service.config;

import com.network_monitor.portal_service.model.dto.request.UserInsertRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.service.MailService;
import com.network_monitor.portal_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class InitAdmin implements CommandLineRunner {

    private final KeyCloakService keyCloakService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final MailService mailService;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.email:admin@monitor.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    @Value("${app.admin.full-name:Super Administrator}")
    private String adminFullName;

    @Value("${app.admin.phone:0909090909}")
    private String adminPhone;

    @Value("${app.admin.role:ROLE_SUPER_ADMIN}")
    private String adminRole;

    @Override
    public void run(String... args) {
        try {
            initAdminAccount();
        } catch (Exception e) {
            log.error("Lỗi trong quá trình khởi tạo tài khoản Admin mặc định: {}", e.getMessage(), e);
        }
    }

    private void initAdminAccount() {
        boolean keycloakExists = checkKeycloakAdminExists();
        boolean postgresExists = checkPostgresAdminExists();

        if (keycloakExists && postgresExists) {
            log.info("Tài khoản Admin đã tồn tại đầy đủ trên cả Keycloak và Postgres. Bỏ qua khởi tạo.");
            return;
        }

        // Tạo Mailbox Stalwart nếu chưa tồn tại
        mailService.createMailAccount(adminUsername, adminPassword, adminFullName);

        String keycloakUserId = syncKeycloakAdmin(keycloakExists);
        syncPostgresAdmin(postgresExists, keycloakUserId);
    }

    private boolean checkKeycloakAdminExists() {
        ApiResponse<Boolean> response = keyCloakService.existsByUsername(adminUsername);
        return response.isSuccess() && Boolean.TRUE.equals(response.getData());
    }

    private boolean checkPostgresAdminExists() {
        return userRepository.findByUsername(adminUsername).isPresent();
    }

    private String syncKeycloakAdmin(boolean exists) {
        if (exists) {
            log.info("Tài khoản Admin đã có trên Keycloak. Lấy Keycloak ID...");
            ApiResponse<String> idResponse = keyCloakService.findUserIdByUsername(adminUsername);
            if (idResponse.isSuccess() && idResponse.getData() != null) {
                return idResponse.getData();
            }
            throw new IllegalStateException("Không tìm thấy UUID Keycloak cho user " + adminUsername);
        }

        log.info("Tạo tài khoản Admin mới trên Keycloak...");
        ApiResponse<String> createResponse = keyCloakService.createAndConfigureUser(
                adminUsername,
                adminEmail,
                adminFullName,
                adminPassword,
                adminRole,
                false
        );

        if (createResponse.isSuccess() && createResponse.getData() != null) {
            return createResponse.getData();
        }

        throw new IllegalStateException("Không thể khởi tạo Admin trên Keycloak: " + createResponse.getErrorDetails());
    }

    private void syncPostgresAdmin(boolean exists, String keycloakUserId) {
        if (exists) {
            log.info("Tài khoản Admin đã có trong cơ sở dữ liệu Postgres.");
            return;
        }

        log.info("Tạo tài khoản Admin trong cơ sở dữ liệu Postgres với Keycloak UUID: {}", keycloakUserId);
        
        User adminUser = User.builder()
                .id(keycloakUserId)
                .username(adminUsername)
                .email(adminEmail)
                .fullName(adminFullName)
                .phoneNumber(adminPhone)
                .roleName(adminRole)
                .isActive(true)
                .build();

        userRepository.save(adminUser);
        log.info("Khởi tạo tài khoản Admin mặc định hoàn tất thành công!");
    }
}
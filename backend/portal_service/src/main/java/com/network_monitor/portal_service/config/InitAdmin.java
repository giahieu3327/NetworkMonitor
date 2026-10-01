package com.network_monitor.portal_service.config;

import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.KeyCloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class InitAdmin implements CommandLineRunner {

    private final KeyCloakService keyCloakService;
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

        /*
         * Nếu tài khoản đã tồn tại đầy đủ ở cả Keycloak và PostgreSQL
         * thì không cần làm gì thêm.
         */
        if (keycloakExists && postgresExists) {

            log.info(
                    "Tài khoản Super Admin đã tồn tại đầy đủ trên cả "
                            + "Keycloak và PostgreSQL. Bỏ qua khởi tạo."
            );

            return;
        }

        /*
         * Đảm bảo tài khoản tồn tại trên Keycloak trước.
         */
        String keycloakUserId = syncKeycloakAdmin(keycloakExists);

        /*
         * Sau đó đồng bộ sang PostgreSQL.
         */
        syncPostgresAdmin(postgresExists, keycloakUserId);
    }


    /**
     * Kiểm tra Super Admin đã tồn tại trên Keycloak hay chưa.
     */
    private boolean checkKeycloakAdminExists() {

        ApiResponse<Boolean> response =
                keyCloakService.existsByUsername(adminUsername);

        return response.isSuccess()
                && Boolean.TRUE.equals(response.getData());
    }


    /**
     * Kiểm tra Super Admin đã tồn tại trong PostgreSQL hay chưa.
     */
    private boolean checkPostgresAdminExists() {

        return userRepository
                .findByUsername(adminUsername)
                .isPresent();
    }


    /**
     * Đảm bảo Super Admin tồn tại trên Keycloak.
     *
     * Không gửi email xác thực ở bước này.
     *
     * emailVerified = false
     * và admin có thể xác thực email sau thông qua flow
     * xác thực email của hệ thống.
     */
    private String syncKeycloakAdmin(boolean exists) {

        if (exists) {

            log.info(
                    "Tài khoản Super Admin đã tồn tại trên Keycloak. "
                            + "Lấy Keycloak ID..."
            );

            ApiResponse<String> idResponse =
                    keyCloakService.findUserIdByUsername(adminUsername);

            if (idResponse.isSuccess()
                    && idResponse.getData() != null) {

                return idResponse.getData();
            }

            throw new IllegalStateException(
                    "Không tìm thấy UUID Keycloak cho user "
                            + adminUsername
            );
        }


        log.info(
                "Tạo tài khoản Super Admin mới trên Keycloak. "
                        + "Không gửi email xác thực."
        );

        /*
         * createAndConfigureUser() hiện tại của bạn:
         *
         * 1. Tạo user
         * 2. Set password
         * 3. Assign role
         *
         * Không thực hiện gửi email xác thực.
         */
        ApiResponse<String> createResponse =
                keyCloakService.createAndConfigureUser(
                        adminUsername,
                        adminEmail,
                        adminFullName,
                        adminPassword,
                        adminRole
                );

        if (createResponse.isSuccess()
                && createResponse.getData() != null) {

            return createResponse.getData();
        }

        throw new IllegalStateException(
                "Không thể khởi tạo Super Admin trên Keycloak: "
                        + createResponse.getErrorDetails()
        );
    }


    /**
     * Đồng bộ Super Admin từ Keycloak sang PostgreSQL.
     */
    private void syncPostgresAdmin(
            boolean exists,
            String keycloakUserId
    ) {

        if (exists) {

            log.info(
                    "Tài khoản Super Admin đã tồn tại "
                            + "trong cơ sở dữ liệu PostgreSQL."
            );

            return;
        }


        log.info(
                "Đồng bộ Super Admin từ Keycloak sang PostgreSQL. "
                        + "Keycloak UUID: {}",
                keycloakUserId
        );


        /*
         * Lấy thông tin user thực tế từ Keycloak.
         *
         * Mục đích:
         * - Lấy createdTimestamp từ Keycloak
         * - Lấy emailVerified thực tế
         * - Tránh tự tạo createdAt bằng thời gian của PostgreSQL
         */
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


        /*
         * Keycloak trả createdTimestamp dạng milliseconds.
         *
         * Ví dụ:
         * 1758000000000
         *
         * -> OffsetDateTime UTC
         */
        OffsetDateTime createdAt;

        if (keycloakUser.getCreatedTimestamp() != null) {

            createdAt = Instant
                    .ofEpochMilli(keycloakUser.getCreatedTimestamp())
                    .atOffset(ZoneOffset.UTC);

        } else {

            /*
             * Trường hợp hiếm Keycloak không trả createdTimestamp.
             * Không nên để NULL vì PostgreSQL đang NOT NULL.
             */
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }


        /*
         * emailVerified lấy trực tiếp từ Keycloak.
         *
         * User mới được tạo bởi hệ thống của bạn:
         * emailVerified = false
         *
         * Không gửi email ngay lúc khởi tạo.
         */
        boolean emailVerified =
                Boolean.TRUE.equals(keycloakUser.isEmailVerified());


        User adminUser = User.builder()
                .id(keycloakUserId)
                .username(adminUsername)
                .email(adminEmail)
                .fullName(adminFullName)
                .phoneNumber(adminPhone)
                .roleName(adminRole)
                .isActive(
                        Boolean.TRUE.equals(keycloakUser.isEnabled())
                )
                .emailVerified(emailVerified)
                .createdAt(createdAt)
                .build();


        userRepository.save(adminUser);


        log.info(
                "Khởi tạo tài khoản Super Admin mặc định hoàn tất. "
                        + "username={}, emailVerified={}",
                adminUsername,
                emailVerified
        );
    }
}
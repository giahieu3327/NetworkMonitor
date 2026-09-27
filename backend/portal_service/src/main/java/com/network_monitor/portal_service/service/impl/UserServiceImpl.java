package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.mapper.UserMapper;
import com.network_monitor.portal_service.model.dto.request.UserInsertRequest;
import com.network_monitor.portal_service.model.dto.request.UserUpdateRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.RoleResponse;
import com.network_monitor.portal_service.model.dto.response.UserResponse;
import com.network_monitor.portal_service.model.entity.User;
import com.network_monitor.portal_service.repository.UserRepository;
import com.network_monitor.portal_service.service.KeyCloakService;
import com.network_monitor.portal_service.service.MailService;
import com.network_monitor.portal_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final KeyCloakService keyCloakService;
    private final MailService mailService;

    @Override
    public ApiResponse<Void> insertUser(UserInsertRequest request) {
        // BƯỚC 0: VALIDATE TRONG CSDL CỤC BỘ
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ApiResponse.error("Tạo người dùng thất bại", "Tên đăng nhập " + request.getUsername() + " đã tồn tại");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ApiResponse.error("Tạo người dùng thất bại", "Email " + request.getEmail() + " đã tồn tại");
        }

        String createdKeycloakUserId = null;
        boolean isMailCreated = false;

        try {
            // BƯỚC 1: TẠO HỘP THƯ NỘI BỘ TRÊN STALWART MAIL SERVER
            ApiResponse<Void> mailResult = mailService.createMailAccount(
                    request.getUsername(),
                    request.getPassword(),
                    request.getFullName()
            );

            if (!mailResult.isSuccess()) {
                return ApiResponse.error("Khởi tạo tài khoản thất bại", "Lỗi tạo mailbox Stalwart: " + mailResult.getErrorDetails());
            }
            isMailCreated = true;

            // BƯỚC 2: TẠO VÀ CẤU HÌNH TÀI KHOẢN TRÊN KEYCLOAK (sendVerificationEmail = false)
            String roleName = (request.getRoleName() != null && !request.getRoleName().isBlank()) 
                    ? request.getRoleName() : "ROLE_USER";

            ApiResponse<String> kcResult = keyCloakService.createAndConfigureUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getFullName(),
                    request.getPassword(),
                    roleName,
                    false
            );

            if (!kcResult.isSuccess()) {
                throw new RuntimeException("Lỗi Keycloak: " + kcResult.getErrorDetails());
            }
            createdKeycloakUserId = kcResult.getData();

            // BƯỚC 3: LƯU THÔNG TIN VÀO CSDL POSTGRESQL (SPRING DATA JPA)
            User user = User.builder()
                    .id(createdKeycloakUserId)
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .fullName(request.getFullName())
                    .phoneNumber(request.getPhoneNumber())
                    .roleName(roleName)
                    .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                    .build();

            userRepository.save(user);

            return ApiResponse.success("Khởi tạo tài khoản người dùng, hộp thư và phân quyền thành công");

        } catch (Exception ex) {
            log.error("Lỗi trong quá trình khởi tạo người dùng: {}. Đang thu hồi tài nguyên (Rollback)...", ex.getMessage());

            // THU HỒI TÀI NGUYÊN (ROLLBACK SAGA)
            if (createdKeycloakUserId != null) {
                try {
                    keyCloakService.deleteUser(createdKeycloakUserId);
                    log.info("Rollback: Đã xóa tài khoản Keycloak ID {}", createdKeycloakUserId);
                } catch (Exception e) {
                    log.error("Rollback Keycloak ID {} thất bại: {}", createdKeycloakUserId, e.getMessage());
                }
            }

            if (isMailCreated) {
                try {
                    mailService.deleteMailAccount(request.getUsername());
                    log.info("Rollback: Đã xóa tài khoản Stalwart Mail cho user {}", request.getUsername());
                } catch (Exception e) {
                    log.error("Rollback Stalwart Mail {} thất bại: {}", request.getUsername(), e.getMessage());
                }
            }

            return ApiResponse.error("Tạo người dùng thất bại và đã thu hồi tài nguyên", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> updateUser(String id, UserUpdateRequest request) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + id));

            if (request.getUsername() != null && !request.getUsername().isBlank()) {
                user.setUsername(request.getUsername());
            }

            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                user.setEmail(request.getEmail());
            }

            if (request.getFullName() != null && !request.getFullName().isBlank()) {
                user.setFullName(request.getFullName());
            }

            if (request.getEmail() != null || request.getFullName() != null) {
                keyCloakService.updateKeycloakUser(id, user.getEmail(), user.getFullName());
            }

            if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
                keyCloakService.setPassword(id, request.getNewPassword(), false);
            }

            if (request.getPhoneNumber() != null) {
                user.setPhoneNumber(request.getPhoneNumber());
            }

            if (request.getRoleName() != null && !request.getRoleName().isBlank()) {
                user.setRoleName(request.getRoleName());
                keyCloakService.updateUserRole(id, request.getRoleName());
            }

            if (request.getIsActive() != null) {
                user.setIsActive(request.getIsActive());
                keyCloakService.setUserEnabled(id, request.getIsActive());
            }

            userRepository.save(user);
            return ApiResponse.success("Cập nhật thông tin người dùng thành công");
        } catch (Exception ex) {
            return ApiResponse.error("Cập nhật thông tin thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> deleteUsers(List<String> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("Xóa người dùng thất bại", "Danh sách ID không được để trống");
            }

            List<User> users = userRepository.findAllById(ids);
            if (users.isEmpty()) {
                return ApiResponse.error("Xóa người dùng thất bại", "Không tìm thấy người dùng phù hợp");
            }

            for (User user : users) {
                keyCloakService.deleteUser(user.getId());
                mailService.deleteMailAccount(user.getUsername());
            }

            userRepository.deleteAll(users);
            return ApiResponse.success("Xóa thành công " + users.size() + " người dùng");
        } catch (Exception ex) {
            return ApiResponse.error("Xóa người dùng thất bại", ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<UserResponse> getUserById(String id) {
        return userRepository.findById(id)
                .map(u -> ApiResponse.success("Lấy thông tin người dùng thành công", mapToResponse(u)))
                .orElseGet(() -> ApiResponse.error("Không tìm thấy người dùng", "ID không tồn tại: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(String keyword, Pageable pageable) {
        String cleanKeyword = (keyword != null) ? keyword.trim() : null;

        String sortBy = "created_at";
        String direction = "DESC";

        if (pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();
            sortBy = order.getProperty();
            direction = order.getDirection().name();
        }

        // Gọi MyBatis Mapper để lọc động + phân trang + sắp xếp
        List<User> users = userMapper.searchUsers(
                cleanKeyword,
                pageable.getOffset(),
                pageable.getPageSize(),
                sortBy,
                direction
        );

        long total = userMapper.countSearchUsers(cleanKeyword);

        List<UserResponse> responses = users.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, total);
    }

    @Override
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        return keyCloakService.getAllRealmRoles();
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .roleName(user.getRoleName())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
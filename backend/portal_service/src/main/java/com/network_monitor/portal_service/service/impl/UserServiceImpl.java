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

    @Override
    public ApiResponse<Void> insertUser(UserInsertRequest request) {

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {

            return ApiResponse.error(
                    "Tạo người dùng thất bại",
                    "Tên đăng nhập "
                            + request.getUsername()
                            + " đã tồn tại trong hệ thống"
            );
        }


        if (userRepository.findByEmail(request.getEmail()).isPresent()) {

            return ApiResponse.error(
                    "Tạo người dùng thất bại",
                    "Email "
                            + request.getEmail()
                            + " đã tồn tại trong hệ thống"
            );
        }


        // ============================================================
        // KIỂM TRA PASSWORD + CONFIRM PASSWORD
        // ============================================================

        if (request.getConfirmPassword() == null
                || request.getConfirmPassword().isBlank()) {

            return ApiResponse.error(
                    "Tạo người dùng thất bại",
                    "Xác nhận mật khẩu không được để trống"
            );
        }


        if (!request.getPassword()
                .equals(request.getConfirmPassword())) {

            return ApiResponse.error(
                    "Tạo người dùng thất bại",
                    "Mật khẩu và xác nhận mật khẩu không khớp"
            );
        }


        String createdKeycloakUserId = null;

        try {

            String roleName =
                    (request.getRoleName() != null
                            && !request.getRoleName().isBlank())
                            ? request.getRoleName()
                            : "ROLE_GUEST_VIEWER";


            ApiResponse<String> kcResult =
                    keyCloakService.createAndConfigureUser(
                            request.getUsername(),
                            request.getEmail(),
                            request.getFullName(),
                            request.getPassword(),
                            roleName
                    );


            if (!kcResult.isSuccess()) {

                return ApiResponse.error(
                        "Tạo người dùng trên Keycloak thất bại",
                        kcResult.getErrorDetails()
                );
            }


            createdKeycloakUserId = kcResult.getData();


            User user = User.builder()
                    .id(createdKeycloakUserId)
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .fullName(request.getFullName())
                    .phoneNumber(request.getPhoneNumber())
                    .roleName(roleName)
                    .isActive(
                            request.getIsActive() != null
                                    ? request.getIsActive()
                                    : true
                    )
                    .emailVerified(false)
                    .build();


            userRepository.save(user);


            return ApiResponse.success(
                    "Tạo tài khoản người dùng thành công"
            );

        } catch (Exception ex) {

            log.error(
                    "Lỗi trong quá trình tạo người dùng: {}. "
                            + "Bắt đầu Rollback Keycloak...",
                    ex.getMessage()
            );


            if (createdKeycloakUserId != null) {

                try {

                    keyCloakService.deleteUser(
                            createdKeycloakUserId
                    );

                    log.info(
                            "Rollback: Đã xóa tài khoản Keycloak ID {}",
                            createdKeycloakUserId
                    );

                } catch (Exception e) {

                    log.error(
                            "Rollback Keycloak ID {} thất bại: {}",
                            createdKeycloakUserId,
                            e.getMessage()
                    );
                }
            }


            return ApiResponse.error(
                    "Tạo người dùng thất bại và đã thu hồi tài nguyên",
                    ex.getMessage()
            );
        }
    }

    @Override
    @Transactional
    public ApiResponse<Void> updateUser(
            String id,
            UserUpdateRequest request
    ) {

        try {

            User user = userRepository.findById(id)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Không tìm thấy người dùng với ID: " + id
                            )
                    );


            // ============================================================
            // KIỂM TRA PASSWORD + CONFIRM PASSWORD
            // ============================================================

            if (request.getNewPassword() != null
                    && !request.getNewPassword().isBlank()) {

                if (request.getConfirmNewPassword() == null
                        || request.getConfirmNewPassword().isBlank()) {

                    return ApiResponse.error(
                            "Cập nhật người dùng thất bại",
                            "Xác nhận mật khẩu mới không được để trống"
                    );
                }


                if (!request.getNewPassword()
                        .equals(request.getConfirmNewPassword())) {

                    return ApiResponse.error(
                            "Cập nhật người dùng thất bại",
                            "Mật khẩu mới và xác nhận mật khẩu không khớp"
                    );
                }
            }


            // ============================================================
            // USERNAME
            // ============================================================

            if (request.getUsername() != null
                    && !request.getUsername().isBlank()) {

                user.setUsername(request.getUsername());
            }


            // ============================================================
            // EMAIL
            // ============================================================

            if (request.getEmail() != null
                    && !request.getEmail().isBlank()) {

                user.setEmail(request.getEmail());
            }


            // ============================================================
            // FULL NAME
            // ============================================================

            if (request.getFullName() != null
                    && !request.getFullName().isBlank()) {

                user.setFullName(request.getFullName());
            }


            // ============================================================
            // ĐỒNG BỘ EMAIL / FULL NAME SANG KEYCLOAK
            // ============================================================

            if (request.getEmail() != null
                    || request.getFullName() != null) {

                ApiResponse<Void> keycloakResult =
                        keyCloakService.updateKeycloakUser(
                                id,
                                user.getEmail(),
                                user.getFullName()
                        );

                if (!keycloakResult.isSuccess()) {
                    return keycloakResult;
                }
            }


            // ============================================================
            // PASSWORD
            // ============================================================

            if (request.getNewPassword() != null
                    && !request.getNewPassword().isBlank()) {

                ApiResponse<Void> passwordResult =
                        keyCloakService.setPassword(
                                id,
                                request.getNewPassword(),
                                false
                        );

                if (!passwordResult.isSuccess()) {
                    return passwordResult;
                }
            }


            // ============================================================
            // PHONE
            // ============================================================

            if (request.getPhoneNumber() != null) {

                user.setPhoneNumber(
                        request.getPhoneNumber()
                );
            }


            // ============================================================
            // ROLE
            // ============================================================

            if (request.getRoleName() != null
                    && !request.getRoleName().isBlank()) {

                user.setRoleName(
                        request.getRoleName()
                );

                ApiResponse<Void> roleResult =
                        keyCloakService.updateUserRole(
                                id,
                                request.getRoleName()
                        );

                if (!roleResult.isSuccess()) {
                    return roleResult;
                }
            }


            // ============================================================
            // ACTIVE
            // ============================================================

            if (request.getIsActive() != null) {

                user.setIsActive(
                        request.getIsActive()
                );

                ApiResponse<Void> enabledResult =
                        keyCloakService.setUserEnabled(
                                id,
                                request.getIsActive()
                        );

                if (!enabledResult.isSuccess()) {
                    return enabledResult;
                }
            }


            // ============================================================
            // SAVE POSTGRES
            // ============================================================

            userRepository.save(user);


            return ApiResponse.success(
                    "Cập nhật thông tin người dùng thành công"
            );

        } catch (Exception ex) {

            return ApiResponse.error(
                    "Cập nhật thông tin thất bại",
                    ex.getMessage()
            );
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
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
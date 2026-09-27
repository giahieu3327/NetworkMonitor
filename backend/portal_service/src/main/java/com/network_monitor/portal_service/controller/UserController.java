package com.network_monitor.portal_service.controller;

import com.network_monitor.portal_service.model.dto.request.UserInsertRequest;
import com.network_monitor.portal_service.model.dto.request.UserUpdateRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.RoleResponse;
import com.network_monitor.portal_service.model.dto.response.UserResponse;
import com.network_monitor.portal_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 1. Tạo mới User, Mailbox & Keycloak account (Chỉ SUPER_ADMIN)
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> insertUser(@Valid @RequestBody UserInsertRequest request) {
        ApiResponse<Void> response = userService.insertUser(request);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Cập nhật User khác từ xa (Chỉ SUPER_ADMIN)
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @PathVariable String id,
            @RequestBody UserUpdateRequest request) {
        ApiResponse<Void> response = userService.updateUser(id, request);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 3. Xóa nhiều User hàng loạt (Chỉ SUPER_ADMIN)
    @DeleteMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUsers(@RequestBody List<String> ids) {
        ApiResponse<Void> response = userService.deleteUsers(ids);
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 4. Xem chi tiết User theo ID (SUPER_ADMIN hoặc ADMIN)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable String id) {
        ApiResponse<UserResponse> response = userService.getUserById(id);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // 5. Lấy danh sách User theo Keyword + Phân trang (SUPER_ADMIN hoặc ADMIN)
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<Page<UserResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<UserResponse> result = userService.getUsers(keyword, pageable);
        return ResponseEntity.ok(result);
    }

    // 6. Lấy danh sách Realm Roles từ Keycloak (SUPER_ADMIN hoặc ADMIN)
    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        ApiResponse<List<RoleResponse>> response = userService.getAllRoles();
        if (!response.isSuccess()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}
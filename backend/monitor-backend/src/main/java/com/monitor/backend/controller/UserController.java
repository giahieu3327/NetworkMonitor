package com.monitor.backend.controller;

import com.monitor.backend.model.entity.User;
import com.monitor.backend.service.UserService;
import com.monitor.backend.model.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Page<User>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        
        Page<User> result = userService.getUsers(keyword, pageable);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<User> updateUserStatus(
            @PathVariable String id,
            @Valid @RequestBody com.monitor.backend.model.dto.UserStatusRequest request
    ) {
        User updatedUser = userService.updateUserStatus(id, request.getIsActive());
        return ResponseEntity.ok(updatedUser);
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> resetUserPassword(
            @PathVariable String id,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        userService.resetUserPassword(id, request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String currentUsername = jwt.getClaimAsString("preferred_username");
        userService.deleteUser(id, currentUsername);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<User> updateUserByAdmin(
            @PathVariable String id,
            @Valid @RequestBody com.monitor.backend.model.dto.UpdateUserProfileRequest request
    ) {
        User updatedUser = userService.updateUserProfile(id, request.getEmail(), request.getFullName(), request.getPhoneNumber());
        return ResponseEntity.ok(updatedUser);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable String id,
            @Valid @RequestBody com.monitor.backend.model.dto.UpdateUserRoleRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String currentUsername = jwt.getClaimAsString("preferred_username");
        userService.updateUserRole(id, request.getNewRole(), currentUsername);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        User user = userService.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + id));
        return ResponseEntity.ok(user);
    }
}
package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.request.UserInsertRequest;
import com.network_monitor.portal_service.model.dto.request.UserUpdateRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.RoleResponse;
import com.network_monitor.portal_service.model.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    ApiResponse<Void> insertUser(UserInsertRequest request);
    ApiResponse<Void> updateUser(String id, UserUpdateRequest request);
    ApiResponse<Void> deleteUsers(List<String> ids);

    ApiResponse<UserResponse> getUserById(String id);
    Page<UserResponse> getUsers(String keyword, Pageable pageable);

    ApiResponse<List<RoleResponse>> getAllRoles();
}
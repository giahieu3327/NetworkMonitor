package com.network_monitor.portal_service.service.impl;

import com.network_monitor.portal_service.model.dto.request.LoginRequest;
import com.network_monitor.portal_service.model.dto.request.LogoutRequest;
import com.network_monitor.portal_service.model.dto.request.RefreshTokenRequest;
import com.network_monitor.portal_service.model.dto.response.ApiResponse;
import com.network_monitor.portal_service.model.dto.response.TokenResponse;
import com.network_monitor.portal_service.service.AuthService;
import com.network_monitor.portal_service.service.KeyCloakService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final KeyCloakService keyCloakService;

    @Override
    public ApiResponse<TokenResponse> login(LoginRequest request) {
        return keyCloakService.login(request.getUsername(), request.getPassword());
    }

    @Override
    public ApiResponse<TokenResponse> refreshToken(RefreshTokenRequest request) {
        return keyCloakService.refreshToken(request.getRefreshToken());
    }

    @Override
    public ApiResponse<Void> logout(LogoutRequest request) {
        return keyCloakService.logout(request.getRefreshToken());
    }
}
package com.network_monitor.portal_service.service;

import com.network_monitor.portal_service.model.dto.RoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final KeyCloakService keyCloakService;

    public List<RoleResponse> getAllRoles() {
        return keyCloakService.getAllRealmRoles();
    }
}
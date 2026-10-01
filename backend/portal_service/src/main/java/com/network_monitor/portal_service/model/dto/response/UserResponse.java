package com.network_monitor.portal_service.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String id;

    private String username;

    private String email;

    private String fullName;

    private String phoneNumber;

    private String roleName;

    private Boolean isActive;

    private Boolean emailVerified;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
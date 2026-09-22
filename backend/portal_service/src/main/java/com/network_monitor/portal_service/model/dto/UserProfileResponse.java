package com.network_monitor.portal_service.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private String id;
    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;
    private Boolean isActive;
    private List<String> roles;
}
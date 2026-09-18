package com.monitor.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRoleRequest {

    @NotBlank(message = "Role mới không được để trống")
    private String newRole;
}
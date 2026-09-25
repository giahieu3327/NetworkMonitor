package com.network_monitor.portal_service.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserStatusRequest {

    @NotNull(message = "Trạng thái isActive không được để trống")
    private Boolean isActive;
}
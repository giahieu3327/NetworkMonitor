package com.network_monitor.portal_service.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRequest {

    @NotBlank(message = "Tên thiết bị không được để trống")
    private String deviceName;

    @NotBlank(message = "Địa chỉ IP không được để trống")
    @Pattern(
        regexp = "^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$",
        message = "Địa chỉ IP không hợp lệ"
    )
    private String ipAddress;

    @NotNull(message = "Loại thiết bị không được để trống")
    private String deviceType;

    private String model;
    private String firmwareVersion;

    private String status; // Khi Thêm mới có thể bỏ trống (Service tự gán UP), dùng khi Cập nhật
}
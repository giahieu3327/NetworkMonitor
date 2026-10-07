package com.network_monitor.portal_service.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidConfigRequest {

    @NotNull(message = "Phạm vi metric không được để trống")
    private String metricScope;

    @NotBlank(message = "Loại chỉ số không được để trống")
    private String metricType;

    @NotBlank(message = "Chuỗi OID pattern không được để trống")
    private String oidPattern;

    private String dataType;
    private Double multiplier;
    private String deviceType;
    private Long deviceId;
    private Long interfaceId;
    private Boolean isActive;
    private String description;
}
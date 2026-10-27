
package com.network_monitor.portal_service.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidConfigRequest {

    @NotBlank(message = "Phạm vi metric không được để trống")
    @Pattern(
            regexp = "^(DEVICE|INTERFACE)$",
            message = "Phạm vi metric phải là DEVICE hoặc INTERFACE"
    )
    private String metricScope;

    @NotBlank(message = "Loại chỉ số không được để trống")
    @Size(max = 100, message = "Loại chỉ số không được vượt quá 100 ký tự")
    private String metricType;

    @NotBlank(message = "Chuỗi OID pattern không được để trống")
    private String oidPattern;

    @Size(max = 30, message = "Data type không được vượt quá 30 ký tự")
    private String dataType;

    @DecimalMin(
            value = "0.0",
            inclusive = false,
            message = "Multiplier phải lớn hơn 0"
    )
    private Double multiplier;

    @NotNull(message = "ID thiết bị không được để trống")
    @Min(value = 1, message = "ID thiết bị phải lớn hơn 0")
    private Long deviceId;

    private Integer interfaceIndex;

    private Boolean isActive;
}

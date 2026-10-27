
package com.network_monitor.portal_service.model.dto.request;

import jakarta.validation.constraints.Max;
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
public class DeviceCredentialRequest {

    @NotNull(message = "ID thiết bị không được để trống")
    @Min(value = 1, message = "ID thiết bị phải lớn hơn 0")
    private Long deviceId;

    @NotBlank(message = "Tên thông tin xác thực không được để trống")
    @Size(max = 100, message = "Tên thông tin xác thực không được vượt quá 100 ký tự")
    private String credentialName;

    @NotBlank(message = "Loại thông tin xác thực không được để trống")
    @Pattern(
            regexp = "^(SNMP|SYSLOG)$",
            message = "Loại thông tin xác thực phải là SNMP hoặc SYSLOG"
    )
    private String credentialType;

    @NotBlank(message = "Giao thức không được để trống")
    @Pattern(
            regexp = "^(SNMPV1|SNMPV2C|SNMPV3|UDP|TCP|TLS)$",
            message = "Giao thức không hợp lệ"
    )
    private String protocolType;

    private String communityString;

    @Min(value = 1, message = "Port phải lớn hơn hoặc bằng 1")
    @Max(value = 65535, message = "Port không được vượt quá 65535")
    private Integer port;

    private Boolean isActive;
}

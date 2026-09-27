package com.network_monitor.portal_service.model.dto.request;

import com.network_monitor.portal_service.model.enums.ProtocolType;
import jakarta.validation.constraints.NotNull;
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
    private Long deviceId;

    @NotNull(message = "Loại giao thức không được để trống")
    private ProtocolType protocolType;

    private Integer port;
    private String communityString;
    private Boolean isPrimary;
}
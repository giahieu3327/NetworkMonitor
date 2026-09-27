package com.network_monitor.portal_service.model.dto.response;

import com.network_monitor.portal_service.model.enums.ProtocolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCredentialResponse {

    private Long id;
    private Long deviceId;
    private ProtocolType protocolType;
    private Integer port;
    private String communityString;
    private Boolean isPrimary;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
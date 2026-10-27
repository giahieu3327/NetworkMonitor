package com.network_monitor.portal_service.model.dto.kafka.credential;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCredentialEventDto {

    private Long id;
    private Long deviceId;
    private String credentialName;
    private String credentialType;
    private String protocolType;
    private String communityString;
    private Integer port;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
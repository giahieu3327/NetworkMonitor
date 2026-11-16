package com.network_monitor.portal_service.model.dto.response;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArpMacTableResponse {

    private Long id;

    private Long deviceId;

    private String deviceName;

    private Integer interfaceIndex;

    private String ipAddress;

    private String macAddress;

    private OffsetDateTime updatedAt;
}
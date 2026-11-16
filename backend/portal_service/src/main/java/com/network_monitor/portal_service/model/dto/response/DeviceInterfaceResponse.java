package com.network_monitor.portal_service.model.dto.response;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceInterfaceResponse {

    private Long deviceId;

    private String deviceName;

    private Integer interfaceIndex;

    private String interfaceName;

    private Integer interfaceType;

    private Integer mtu;

    private Long speedBps;

    private String physAddress;

    private String adminStatus;

    private String operStatus;

    private String interfaceAlias;

    private Boolean isMonitor;

    private OffsetDateTime updatedAt;
}
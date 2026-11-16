package com.network_monitor.portal_service.model.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceInterfaceRequest {

    private Long deviceId;

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
}
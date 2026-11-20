package com.network_monitor.portal_service.model.dto.metric;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricConfig {

    private Long deviceId;
    private String deviceName;
    private String ipAddress;
    private String deviceType;

    private List<Credential> credentials = new ArrayList<>();
    private List<Oid> oids = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Credential {
        private String protocolType;
        private String communityString;
        private Integer port;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Oid {
        private String metricScope;
        private String metricType;
        private String oidPattern;
        private String oidKind;
        private String dataType;
        private Double multiplier;
        private Integer interfaceIndex;
        private String interfaceName;
    }
}
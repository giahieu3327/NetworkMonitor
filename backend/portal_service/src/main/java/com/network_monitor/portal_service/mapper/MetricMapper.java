package com.network_monitor.portal_service.mapper;

import com.network_monitor.portal_service.model.dto.metric.MetricConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MetricMapper {

    List<MetricConfig> findDevicesForMetrics();

    List<MetricConfig.Credential> findActiveSnmpCredentials(
            @Param("deviceId") Long deviceId
    );

    List<MetricConfig.Oid> findActiveOidConfigs(
            @Param("deviceId") Long deviceId
    );
}
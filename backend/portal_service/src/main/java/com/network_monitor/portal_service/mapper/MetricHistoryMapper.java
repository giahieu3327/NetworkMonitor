package com.network_monitor.portal_service.mapper;

import com.network_monitor.portal_service.model.dto.response.MetricHistoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MetricHistoryMapper {

    List<MetricHistoryRow> findCpuHistory(
            @Param("deviceId") Long deviceId
    );

    List<MetricHistoryRow> findRamHistory(
            @Param("deviceId") Long deviceId
    );
}
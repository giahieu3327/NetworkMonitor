package com.network_monitor.portal_service.mapper;

import com.network_monitor.portal_service.model.dto.response.DailyIncidentSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface ReportMapper {

    List<DailyIncidentSummaryResponse> summarizeDailyIncidents(
            @Param("deviceId") Long deviceId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );
}
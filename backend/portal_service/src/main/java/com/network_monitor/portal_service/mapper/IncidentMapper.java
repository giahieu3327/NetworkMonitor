package com.network_monitor.portal_service.mapper;

import com.network_monitor.portal_service.model.dto.response.DeviceIncidentMetricSummaryResponse;
import com.network_monitor.portal_service.model.dto.response.IncidentResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface IncidentMapper {

    List<IncidentResponse> searchIncidentsByMetricType(
            @Param("metricType") String metricType,
            @Param("deviceId") Long deviceId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize,
            @Param("sortBy") String sortBy,
            @Param("direction") String direction
    );

    long countIncidentsByMetricType(
            @Param("metricType") String metricType,
            @Param("deviceId") Long deviceId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("keyword") String keyword
    );

    List<DeviceIncidentMetricSummaryResponse> summarizeIncidentsByDevice(
            @Param("deviceId") Long deviceId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize,
            @Param("sortBy") String sortBy,
            @Param("direction") String direction
    );

    long countIncidentMetricsByDevice(
            @Param("deviceId") Long deviceId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("keyword") String keyword
    );
}
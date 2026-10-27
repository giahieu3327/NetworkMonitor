package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.OidConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OidConfigRepository extends JpaRepository<OidConfig, Long> {

    // Lấy toàn bộ cấu hình OID của một thiết bị.
    List<OidConfig> findByDevice_Id(Long deviceId);

    // Lấy cấu hình đang hoạt động của một thiết bị.
    List<OidConfig> findByDevice_IdAndIsActiveTrue(Long deviceId);

    // Lấy cấu hình theo thiết bị và phạm vi thu thập.
    List<OidConfig> findByDevice_IdAndMetricScope(
            Long deviceId,
            String metricScope
    );

    // Lấy cấu hình theo thiết bị và loại chỉ số.
    List<OidConfig> findByDevice_IdAndMetricType(
            Long deviceId,
            String metricType
    );

    // Lấy cấu hình của một interface cụ thể trên thiết bị.
    List<OidConfig> findByDevice_IdAndInterfaceIndex(
            Long deviceId,
            Integer interfaceIndex
    );
}
package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.InterfaceMetric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface InterfaceMetricRepository extends JpaRepository<InterfaceMetric, Long> {

    List<InterfaceMetric> findByDeviceInterfaceIdAndTimestampBetweenOrderByTimestampAsc(
            Long interfaceId, OffsetDateTime start, OffsetDateTime end);

    @Query("SELECT im FROM InterfaceMetric im WHERE im.deviceInterface.id = :interfaceId ORDER BY im.timestamp DESC")
    List<InterfaceMetric> findLatestMetricByInterfaceId(@Param("interfaceId") Long interfaceId, Pageable pageable);
}
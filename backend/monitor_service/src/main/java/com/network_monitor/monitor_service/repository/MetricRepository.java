package com.network_monitor.monitor_service.repository;

import com.network_monitor.monitor_service.model.entity.Metric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricRepository
        extends JpaRepository<Metric, Long> {

    List<Metric> findByDeviceIdAndInterfaceIndexIsNullAndTimestampBetweenOrderByTimestampAsc(
            Long deviceId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<Metric> findByDeviceIdAndInterfaceIndexAndTimestampBetweenOrderByTimestampAsc(
            Long deviceId,
            Integer interfaceIndex,
            LocalDateTime start,
            LocalDateTime end
    );

    List<Metric> findByDeviceIdAndInterfaceIndexIsNullOrderByTimestampDesc(
            Long deviceId,
            Pageable pageable
    );

    List<Metric> findByDeviceIdAndInterfaceIndexOrderByTimestampDesc(
            Long deviceId,
            Integer interfaceIndex,
            Pageable pageable
    );
}
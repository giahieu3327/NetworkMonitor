package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.MetricAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricAggregateRepository
        extends JpaRepository<MetricAggregate, Long> {

    List<MetricAggregate>
    findByDeviceIdAndInterfaceIndexIsNullAndAggregationWindowAndPeriodStartBetweenOrderByPeriodStartAsc(
            Long deviceId,
            String aggregationWindow,
            LocalDateTime start,
            LocalDateTime end
    );

    List<MetricAggregate>
    findByDeviceIdAndInterfaceIndexAndAggregationWindowAndPeriodStartBetweenOrderByPeriodStartAsc(
            Long deviceId,
            Integer interfaceIndex,
            String aggregationWindow,
            LocalDateTime start,
            LocalDateTime end
    );
}
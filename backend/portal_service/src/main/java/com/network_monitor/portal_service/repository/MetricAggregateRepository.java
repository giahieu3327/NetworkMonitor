package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.MetricAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MetricAggregateRepository extends JpaRepository<MetricAggregate, Long> {

    Optional<MetricAggregate> findByDeviceIdAndAggregationWindowAndPeriodStart(
            Long deviceId,
            String aggregationWindow,
            OffsetDateTime periodStart
    );

    List<MetricAggregate> findByDeviceIdAndAggregationWindowAndPeriodStartGreaterThanEqualOrderByPeriodStartAsc(
            Long deviceId,
            String aggregationWindow,
            OffsetDateTime since
    );

    List<MetricAggregate> findByDeviceIdAndAggregationWindowAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAsc(
            Long deviceId,
            String aggregationWindow,
            OffsetDateTime start,
            OffsetDateTime end
    );

    List<MetricAggregate> findByAggregationWindowAndPeriodStartGreaterThanEqualOrderByPeriodStartAscDeviceIdAsc(
            String aggregationWindow,
            OffsetDateTime since
    );

    List<MetricAggregate> findByAggregationWindowAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAscDeviceIdAsc(
            String aggregationWindow,
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Modifying
    @Query("DELETE FROM MetricAggregate m WHERE m.periodEnd < :cutoff")
    int deleteExpiredAggregates(@Param("cutoff") OffsetDateTime cutoff);
}
package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.MetricAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetricAggregateRepository
        extends JpaRepository<MetricAggregate, Long> {
}
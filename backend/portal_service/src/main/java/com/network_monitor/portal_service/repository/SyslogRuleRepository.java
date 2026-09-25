package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.SyslogRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyslogRuleRepository extends JpaRepository<SyslogRule, Long> {

    List<SyslogRule> findByIsEnabledTrue();

    @Query("SELECT s FROM SyslogRule s WHERE s.isEnabled = true AND " +
           "(s.device.id = :deviceId OR s.device.id IS NULL)")
    List<SyslogRule> findActiveRulesForDevice(@Param("deviceId") Long deviceId);
}
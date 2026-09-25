package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Incident;
import com.network_monitor.portal_service.model.enums.IncidentSeverity;
import com.network_monitor.portal_service.model.enums.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByDeviceIdAndStatus(Long deviceId, IncidentStatus status);

    Optional<Incident> findByDeviceIdAndThresholdRuleIdAndStatus(Long deviceId, Long ruleId, IncidentStatus status);

    @Query("SELECT i FROM Incident i WHERE " +
           "(:deviceId IS NULL OR i.device.id = :deviceId) AND " +
           "(:status IS NULL OR i.status = :status) AND " +
           "(:severity IS NULL OR i.severity = :severity)")
    Page<Incident> filterIncidents(
            @Param("deviceId") Long deviceId,
            @Param("status") IncidentStatus status,
            @Param("severity") IncidentSeverity severity,
            Pageable pageable);
}
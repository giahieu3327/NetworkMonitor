package com.monitor.backend.repository;

import com.monitor.backend.model.entity.Incident;
import com.monitor.backend.model.enums.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByStatus(IncidentStatus status);
    List<Incident> findByDeviceId(Long deviceId);
}

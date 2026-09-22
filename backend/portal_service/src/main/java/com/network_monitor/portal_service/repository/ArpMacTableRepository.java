package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.ArpMacTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArpMacTableRepository extends JpaRepository<ArpMacTable, Long> {
    Optional<ArpMacTable> findByIpAddress(String ipAddress);
    Optional<ArpMacTable> findByMacAddress(String macAddress);
}

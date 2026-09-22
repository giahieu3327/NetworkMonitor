package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.enums.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByIpAddress(String ipAddress);
    List<Device> findByStatus(DeviceStatus status);
}

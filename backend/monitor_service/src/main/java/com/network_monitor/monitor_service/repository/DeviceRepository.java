package com.network_monitor.monitor_service.repository;

import com.network_monitor.monitor_service.model.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByIpAddress(String ipAddress);

    Optional<Device> findByDeviceName(String deviceName);
}
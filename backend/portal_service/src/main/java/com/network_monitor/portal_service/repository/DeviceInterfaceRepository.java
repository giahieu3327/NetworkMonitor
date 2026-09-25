package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.DeviceInterface;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceInterfaceRepository extends JpaRepository<DeviceInterface, Long> {

    List<DeviceInterface> findByDeviceId(Long deviceId);

    Optional<DeviceInterface> findByDeviceIdAndInterfaceIndex(Long deviceId, Integer interfaceIndex);

    Optional<DeviceInterface> findByDeviceIdAndInterfaceName(Long deviceId, String interfaceName);
}
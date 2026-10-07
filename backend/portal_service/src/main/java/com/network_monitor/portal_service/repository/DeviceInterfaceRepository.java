package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.DeviceInterface;
import com.network_monitor.portal_service.model.entity.DeviceInterface.DeviceInterfaceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceInterfaceRepository
        extends JpaRepository<DeviceInterface, DeviceInterfaceId> {

    List<DeviceInterface> findByDeviceId(Long deviceId);

    Optional<DeviceInterface> findByDeviceIdAndInterfaceIndex(
            Long deviceId,
            Integer interfaceIndex
    );

    Optional<DeviceInterface> findByDeviceIdAndInterfaceName(
            Long deviceId,
            String interfaceName
    );
}
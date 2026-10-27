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

    // Lấy danh sách interface của một thiết bị.
    List<DeviceInterface> findByDeviceId(Long deviceId);

    // Tìm interface theo ID thiết bị và chỉ số interface.
    Optional<DeviceInterface> findByDeviceIdAndInterfaceIndex(
            Long deviceId,
            Integer interfaceIndex
    );

    // Tìm interface theo ID thiết bị và tên interface.
    Optional<DeviceInterface> findByDeviceIdAndInterfaceName(
            Long deviceId,
            String interfaceName
    );
}
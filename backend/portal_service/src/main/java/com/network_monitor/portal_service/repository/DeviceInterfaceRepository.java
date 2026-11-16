package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.DeviceInterface;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceInterfaceRepository
        extends JpaRepository<
                DeviceInterface,
                DeviceInterface.DeviceInterfaceId> {
        
        List<DeviceInterface> findAllByDeviceId(Long deviceId);
}
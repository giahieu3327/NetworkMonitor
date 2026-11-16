package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.DeviceInterface;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceInterfaceRepository
        extends JpaRepository<
                DeviceInterface,
                DeviceInterface.DeviceInterfaceId> {
}
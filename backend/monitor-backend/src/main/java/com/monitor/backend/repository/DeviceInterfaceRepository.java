package com.monitor.backend.repository;

import com.monitor.backend.model.entity.DeviceInterface;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceInterfaceRepository extends JpaRepository<DeviceInterface, Long> {
    List<DeviceInterface> findByDeviceId(Long deviceId);
}

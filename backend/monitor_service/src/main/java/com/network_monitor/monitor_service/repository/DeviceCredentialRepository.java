package com.network_monitor.monitor_service.repository;

import com.network_monitor.monitor_service.model.entity.DeviceCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, Integer> {
    List<DeviceCredential> findByDeviceId(Long deviceId);

    Optional<DeviceCredential> findByDeviceIdAndProtocolType(
            Long deviceId,
            String protocolType
    );

    Optional<DeviceCredential> findByDeviceIdAndProtocolTypeAndIsPrimaryTrue(
            Long deviceId,
            String protocolType
    );
}
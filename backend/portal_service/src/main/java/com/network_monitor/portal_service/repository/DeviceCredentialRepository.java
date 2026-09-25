package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.DeviceCredential;
import com.network_monitor.portal_service.model.enums.ProtocolType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, Long> {

    List<DeviceCredential> findByDeviceId(Long deviceId);

    Optional<DeviceCredential> findByDeviceIdAndProtocolType(Long deviceId, ProtocolType protocolType);

    Optional<DeviceCredential> findByDeviceIdAndProtocolTypeAndIsPrimaryTrue(Long deviceId, ProtocolType protocolType);
}
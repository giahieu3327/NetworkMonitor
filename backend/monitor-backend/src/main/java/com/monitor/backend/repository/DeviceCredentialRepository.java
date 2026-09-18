package com.monitor.backend.repository;

import com.monitor.backend.model.entity.DeviceCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, Long> {
    List<DeviceCredential> findByDeviceId(Long deviceId);
    Optional<DeviceCredential> findByDeviceIdAndIsPrimaryTrue(Long deviceId);
}

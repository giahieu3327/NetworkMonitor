package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.DeviceCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceCredentialRepository
        extends JpaRepository<DeviceCredential, Long> {

    // Lấy toàn bộ thông tin xác thực của một thiết bị.
    List<DeviceCredential> findByDevice_Id(Long deviceId);

    // Lấy thông tin xác thực theo thiết bị và loại credential.
    List<DeviceCredential> findByDevice_IdAndCredentialType(
            Long deviceId,
            String credentialType
    );

    // Lấy thông tin xác thực theo thiết bị và giao thức.
    List<DeviceCredential> findByDevice_IdAndProtocolType(
            Long deviceId,
            String protocolType
    );

    // Lấy thông tin xác thực đang hoạt động của một thiết bị.
    List<DeviceCredential> findByDevice_IdAndIsActiveTrue(
            Long deviceId
    );

    // Lấy thông tin xác thực theo thiết bị, giao thức và cổng.
    List<DeviceCredential> findByDevice_IdAndProtocolTypeAndPort(
            Long deviceId,
            String protocolType,
            Integer port
    );
}
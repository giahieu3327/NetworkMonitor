package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // Tìm thiết bị theo địa chỉ IP.
    Optional<Device> findByIpAddress(String ipAddress);

    // Tìm thiết bị theo tên.
    Optional<Device> findByDeviceName(String deviceName);

    // Lấy danh sách thiết bị theo trạng thái.
    List<Device> findByStatus(String status);

    // Lấy danh sách thiết bị theo loại.
    List<Device> findByDeviceType(String deviceType);
}
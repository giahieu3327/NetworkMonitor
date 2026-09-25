package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.Device;
import com.network_monitor.portal_service.model.enums.DeviceStatus;
import com.network_monitor.portal_service.model.enums.DeviceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByIpAddress(String ipAddress);

    List<Device> findByStatus(DeviceStatus status);

    List<Device> findByDeviceType(DeviceType deviceType);

    @Query("SELECT d FROM Device d WHERE " +
           "(:keyword IS NULL OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(d.ipAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(d.model) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Device> searchDevices(@Param("keyword") String keyword, Pageable pageable);
}
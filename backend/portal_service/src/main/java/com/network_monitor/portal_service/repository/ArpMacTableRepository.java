package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.ArpMacTable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArpMacTableRepository
        extends JpaRepository<ArpMacTable, Long> {

    List<ArpMacTable> findAllByDevice_Id(Long deviceId);

    boolean existsByDevice_IdAndIpAddress(
            Long deviceId,
            String ipAddress
    );

    boolean existsByDevice_IdAndIpAddressAndIdNot(
            Long deviceId,
            String ipAddress,
            Long id
    );
}
package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.ArpMacTable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArpMacTableRepository extends JpaRepository<ArpMacTable, Long> {

    Optional<ArpMacTable> findByIpAddressAndDeviceId(String ipAddress, Long deviceId);

    Optional<ArpMacTable> findByMacAddressAndDeviceId(String macAddress, Long deviceId);

    @Query("SELECT a FROM ArpMacTable a WHERE " +
           "(:keyword IS NULL OR LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.macAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.interfaceName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ArpMacTable> searchArpMacEntries(@Param("keyword") String keyword, Pageable pageable);
}
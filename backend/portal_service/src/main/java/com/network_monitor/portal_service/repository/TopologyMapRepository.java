package com.network_monitor.portal_service.repository;

import com.network_monitor.portal_service.model.entity.TopologyMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TopologyMapRepository extends JpaRepository<TopologyMap, Long> {

    Optional<TopologyMap> findByMapName(String mapName);
}
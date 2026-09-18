package com.monitor.backend.repository;

import com.monitor.backend.model.entity.TopologyMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopologyMapRepository extends JpaRepository<TopologyMap, Long> {
}

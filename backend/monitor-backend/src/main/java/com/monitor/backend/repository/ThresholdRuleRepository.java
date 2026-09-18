package com.monitor.backend.repository;

import com.monitor.backend.model.entity.ThresholdRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThresholdRuleRepository extends JpaRepository<ThresholdRule, Long> {
    List<ThresholdRule> findByIsEnabledTrue();
}

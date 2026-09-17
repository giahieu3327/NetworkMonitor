package com.monitor.backend.healthcheck;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatabaseHealthCheck {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health/db")
    public String checkDatabase() {
        try {
            jdbcTemplate.execute("SELECT 1");
            return "PostgreSQL connection OK";
        } catch (Exception e) {
            return "PostgreSQL connection FAILED: " + e.getMessage();
        }
    }
}

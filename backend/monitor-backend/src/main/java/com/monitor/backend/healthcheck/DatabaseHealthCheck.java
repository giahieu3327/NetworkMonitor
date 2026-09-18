package com.monitor.backend.healthcheck;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthCheck implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthCheck(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("SELECT 1");
            System.out.println("[OK] PostgreSQL connection OK");
        } catch (Exception e) {
            System.out.println("[FAILED] PostgreSQL connection FAILED");
            System.out.println("        Error: " + e.getMessage());
        }
    }
}
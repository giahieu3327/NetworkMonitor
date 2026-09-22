package com.network_monitor.portal_service.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    // 1. Primary DataSource (monitor_db cho Main App, JPA, Flyway, MyBatis)
    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource primaryDataSource() {
        return new HikariDataSource();
    }

    // 2. Camunda DataSource (camunda_db cho Camunda Engine)
    @Bean(name = "camundaBpmDataSource")
    @ConfigurationProperties("spring.camunda-datasource.hikari")
    public DataSource camundaBpmDataSource() {
        return new HikariDataSource();
    }
}
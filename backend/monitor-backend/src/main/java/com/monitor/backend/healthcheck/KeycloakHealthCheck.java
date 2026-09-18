package com.monitor.backend.healthcheck;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class KeycloakHealthCheck implements ApplicationRunner {

    private final RestTemplate restTemplate;

    private final String keycloakUrl =
            "http://localhost:8080/realms/monitor-realm";

    public KeycloakHealthCheck() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            restTemplate.getForObject(keycloakUrl, String.class);
            System.out.println("[OK] Keycloak connection OK");
        } catch (Exception e) {
            System.out.println("[FAILED] Keycloak connection FAILED");
            System.out.println("        Error: " + e.getMessage());
        }
    }
}
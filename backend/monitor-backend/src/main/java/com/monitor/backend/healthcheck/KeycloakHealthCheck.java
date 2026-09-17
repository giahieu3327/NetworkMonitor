package com.monitor.backend.healthcheck;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class KeycloakHealthCheck {

    private final String keycloakUrl = "http://localhost:8080/realms/monitor-realm";

    @GetMapping("/health/keycloak")
    public String checkKeycloak() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getForObject(keycloakUrl, String.class);
            return "Keycloak connection OK";
        } catch (Exception e) {
            return "Keycloak connection FAILED: " + e.getMessage();
        }
    }
}

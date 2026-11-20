package com.network_monitor.portal_service.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class Health implements CommandLineRunner {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JavaMailSenderImpl mailSender;

    @Value("${keycloak.server-url:http://localhost:8080}")
    private String keycloakServerUrl;

    /**
     * Tự động chạy ngay sau khi Spring Context khởi động hoàn tất
     */
    @Override
    public void run(String... args) {
        log.info("=================================================");
        log.info("  STARTING STARTUP HEALTH CHECKS");
        log.info("=================================================");
        
        Map<String, Object> healthStatus = checkAllHealth();

        log.info("=================================================");
        log.info("  STARTUP HEALTH CHECK RESULTS");
        log.info("=================================================");
        log.info("PostgreSQL   : {}", (boolean) healthStatus.get("postgresql") ? "OK" : "FAILED");
        log.info("Redis        : {}", (boolean) healthStatus.get("redis") ? "OK" : "FAILED");
        log.info("Keycloak     : {}", (boolean) healthStatus.get("keycloak") ? "OK" : "FAILED");
        log.info("Kafka        : {}", (boolean) healthStatus.get("kafka") ? "OK" : "FAILED");
        log.info("Mail Server  : {}", (boolean) healthStatus.get("mailServer") ? "OK" : "FAILED");

        @SuppressWarnings("unchecked")
        Map<String, Boolean> pings = (Map<String, Boolean>) healthStatus.get("ping");
        pings.forEach((ip, result) -> log.info("Ping [{}] : {}", ip, result ? "OK" : "FAILED"));

        @SuppressWarnings("unchecked")
        Map<String, Boolean> ports = (Map<String, Boolean>) healthStatus.get("port");
        ports.forEach((target, result) -> log.info("Port [{}] : {}", target, result ? "OK" : "FAILED"));

        log.info("OVERALL STATUS: {}", healthStatus.get("status"));
        log.info("=================================================");
    }

    /**
     * 1. Kiểm tra kết nối PostgreSQL
     */
    public boolean checkPostgresql() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            return statement.execute("SELECT 1");
        } catch (Exception e) {
            log.error("PostgreSQL Health Check Failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 2. Kiểm tra kết nối Redis
     */
    public boolean checkRedis() {
        try {
            String pingResult = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equalsIgnoreCase(pingResult);
        } catch (Exception e) {
            log.error("Redis Health Check Failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 3. Kiểm tra kết nối Keycloak
     */
    public boolean checkKeycloak() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            var response = restTemplate.getForEntity(keycloakServerUrl, String.class);
            return response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection();
        } catch (Exception e) {
            log.error("Keycloak Health Check Failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 4. Kiểm tra kết nối Kafka Broker
     */
    public boolean checkKafka() {
        try {
            var metrics = kafkaTemplate.getProducerFactory().createProducer().metrics();
            return metrics != null && !metrics.isEmpty();
        } catch (Exception e) {
            log.error("Kafka Health Check Failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 5. Kiểm tra kết nối Mail Server
     */
    public boolean checkMailServer() {
        try {
            String host = mailSender.getHost();
            int port = mailSender.getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 3000); // Timeout 3s
                return socket.isConnected();
            }
        } catch (Exception e) {
            log.error("Mail Server Health Check Failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 6. Kiểm tra Ping bằng lệnh hệ thống (ICMP Ping)
     */
    public boolean checkPing(String host) {
        try {
            // -c 1: Gửi 1 gói tin
            // -W 3: Wait timeout 3 giây
            ProcessBuilder processBuilder = new ProcessBuilder("ping", "-c", "1", "-W", "3", host);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            
            // Exit code = 0 nghĩa là ping thành công
            return exitCode == 0;
        } catch (Exception e) {
            log.error("Ping Health Check Failed for host {}: {}", host, e.getMessage());
            return false;
        }
    }

    /**
     * 7. Kiểm tra Port Socket
     */
    public boolean checkPort(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000); // Timeout 3s
            return socket.isConnected();
        } catch (IOException e) {
            log.error("Port Check Failed for {}:{} - {}", host, port, e.getMessage());
            return false;
        }
    }

    /**
     * Hàm tổng hợp gọi cả 7 hàm kiểm tra
     */
    public Map<String, Object> checkAllHealth() {
        Map<String, Object> results = new HashMap<>();

        // 1. PostgreSQL
        results.put("postgresql", checkPostgresql());

        // 2. Redis
        results.put("redis", checkRedis());

        // 3. Keycloak
        results.put("keycloak", checkKeycloak());

        // 4. Kafka
        results.put("kafka", checkKafka());

        // 5. Mail Server
        results.put("mailServer", checkMailServer());

        // 6. Ping ICMP kiểm tra 3 địa chỉ IP
        List<String> ipList = List.of("172.16.200.1", "172.16.200.65", "172.16.200.66");
        Map<String, Boolean> pingResults = new HashMap<>();
        for (String ip : ipList) {
            pingResults.put(ip, checkPing(ip));
        }
        results.put("ping", pingResults);

        // 7. Kiểm tra Socket Port chi tiết
        Map<String, Boolean> portResults = new HashMap<>();
        portResults.put("172.16.200.1:80", checkPort("172.16.200.1", 80));
        portResults.put("172.16.200.65:4434", checkPort("172.16.200.65", 4434));
        portResults.put("172.16.200.66:80", checkPort("172.16.200.66", 80));
        results.put("port", portResults);

        // Trạng thái tổng (overall status)
        boolean isServicesHealthy = results.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("ping") && !entry.getKey().equals("port"))
                .allMatch(entry -> (Boolean) entry.getValue());

        boolean isPingHealthy = pingResults.values().stream().allMatch(Boolean::booleanValue);
        boolean isPortHealthy = portResults.values().stream().allMatch(Boolean::booleanValue);

        results.put("status", (isServicesHealthy && isPingHealthy && isPortHealthy) ? "UP" : "DOWN");

        return results;
    }
}
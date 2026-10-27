
package com.network_monitor.monitor_service.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class Health implements CommandLineRunner {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${monitoring.syslog.port:5140}")
    private int syslogPort;

    /**
     * Tự động chạy kiểm tra khi Monitor Service khởi động.
     */
    @Override
    public void run(String... args) {
        log.info("=================================================");
        log.info("  STARTING MONITOR SERVICE HEALTH CHECKS");
        log.info("=================================================");

        Map<String, Object> healthStatus = checkAllHealth();

        log.info("=================================================");
        log.info("  MONITOR SERVICE HEALTH CHECK RESULTS");
        log.info("=================================================");

        log.info("Kafka Broker    : {}",
                Boolean.TRUE.equals(healthStatus.get("kafka")) ? "OK" : "FAILED");

        log.info("Syslog UDP Port : {}",
                Boolean.TRUE.equals(healthStatus.get("syslogPort")) ? "OK" : "FAILED");

        @SuppressWarnings("unchecked")
        Map<String, Boolean> pings =
                (Map<String, Boolean>) healthStatus.get("ping");

        if (pings != null) {
            pings.forEach((ip, result) ->
                    log.info("Ping [{}] : {}",
                            ip, Boolean.TRUE.equals(result) ? "OK" : "FAILED"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Boolean> ports =
                (Map<String, Boolean>) healthStatus.get("port");

        if (ports != null) {
            ports.forEach((target, result) ->
                    log.info("Port [{}] : {}",
                            target, Boolean.TRUE.equals(result) ? "OK" : "FAILED"));
        }

        log.info("OVERALL STATUS   : {}", healthStatus.get("status"));
        log.info("=================================================");
    }

    /**
     * Kiểm tra kết nối Kafka Broker.
     */
    public boolean checkKafka() {
        try (var producer = kafkaTemplate.getProducerFactory().createProducer()) {
            var metrics = producer.metrics();
            return metrics != null && !metrics.isEmpty();
        } catch (Exception e) {
            log.error("Kafka Health Check Failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra UDP port dùng cho Syslog.
     *
     * Nếu bind thành công, port đang khả dụng.
     * Nếu port đã bị chiếm dụng, giả định Syslog Listener đang sử dụng port.
     */
    public boolean checkSyslogPort() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(syslogPort));
            return true;
        } catch (BindException e) {
            log.info(
                    "Syslog UDP port {} is already bound; assuming the listener is running.",
                    syslogPort
            );
            return true;
        } catch (Exception e) {
            log.error(
                    "Syslog UDP Port Check Failed for port {}: {}",
                    syslogPort, e.getMessage()
            );
            return false;
        }
    }

    /**
     * Kiểm tra ICMP Ping bằng lệnh hệ thống Linux.
     */
    public boolean checkPing(String host) {
        try {
            ProcessBuilder processBuilder =
                    new ProcessBuilder("ping", "-c", "1", "-W", "3", host);

            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            boolean completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                log.warn("Ping timed out for host {}", host);
                return false;
            }

            return process.exitValue() == 0;
        } catch (Exception e) {
            log.error(
                    "Ping Health Check Failed for host {}: {}",
                    host, e.getMessage()
            );
            return false;
        }
    }

    /**
     * Kiểm tra kết nối TCP đến host và port.
     */
    public boolean checkPort(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return socket.isConnected();
        } catch (IOException e) {
            log.warn(
                    "Port Check Failed for {}:{} - {}",
                    host, port, e.getMessage()
            );
            return false;
        }
    }

    /**
     * Tổng hợp trạng thái Kafka, Syslog, Ping và TCP port.
     */
    public Map<String, Object> checkAllHealth() {
        Map<String, Object> results = new HashMap<>();

        // 1. Kiểm tra Kafka Broker
        results.put("kafka", checkKafka());

        // 2. Kiểm tra Syslog UDP Port
        results.put("syslogPort", checkSyslogPort());

        // 3. Ping ICMP các thiết bị
        List<String> ipList = List.of(
                "172.16.200.1",
                "172.16.200.65",
                "172.16.200.66"
        );

        Map<String, Boolean> pingResults = new HashMap<>();

        for (String ip : ipList) {
            pingResults.put(ip, checkPing(ip));
        }

        results.put("ping", pingResults);

        // 4. Kiểm tra TCP port các thiết bị
        Map<String, Boolean> portResults = new HashMap<>();

        portResults.put(
                "172.16.200.1:80",
                checkPort("172.16.200.1", 80)
        );

        portResults.put(
                "172.16.200.65:4434",
                checkPort("172.16.200.65", 4434)
        );

        portResults.put(
                "172.16.200.66:80",
                checkPort("172.16.200.66", 80)
        );

        results.put("port", portResults);

        // 5. Tính trạng thái tổng
        boolean isLocalHealthy =
                Boolean.TRUE.equals(results.get("kafka"))
                && Boolean.TRUE.equals(results.get("syslogPort"));

        boolean isPingHealthy =
                pingResults.values().stream()
                        .allMatch(Boolean.TRUE::equals);

        boolean isPortHealthy =
                portResults.values().stream()
                        .allMatch(Boolean.TRUE::equals);

        String overallStatus =
                isLocalHealthy && isPingHealthy && isPortHealthy
                        ? "UP"
                        : "DOWN";

        results.put("status", overallStatus);

        return results;
    }
}

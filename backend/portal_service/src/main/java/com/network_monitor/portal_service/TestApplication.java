package com.network_monitor.portal_service;

import com.network_monitor.portal_service.service.SnmpService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestApplication implements CommandLineRunner {

    private final SnmpService snmpService;

    @Override
    public void run(String... args) {

        //var result1 = snmpService.pollInterfaceScope(4L);
        //var result2 = snmpService.pollInterfaceScope(5L);
        //var result3 = snmpService.pollInterfaceScope(6L);
        System.out.println("========== INTERFACE DEBUG ==========");
        System.out.println("Device 1: ");
        //System.out.println(result1);
        System.out.println("Device 2: ");
        //System.out.println(result2);
        System.out.println("Device 3: ");
        //System.out.println(result3);
        System.out.println("=====================================");
    }
}
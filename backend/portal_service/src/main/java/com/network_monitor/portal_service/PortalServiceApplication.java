package com.network_monitor.portal_service;

import io.github.cdimascio.dotenv.Dotenv; 

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PortalServiceApplication {

	public static void main(String[] args) {
		// Nạp file .env vào System Properties trước khi Spring khởi động
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });
		
		SpringApplication.run(PortalServiceApplication.class, args);
	}

}

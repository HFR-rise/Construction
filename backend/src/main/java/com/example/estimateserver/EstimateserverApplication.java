package com.example.estimateserver;

import com.example.estimateserver.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EstimateserverApplication implements CommandLineRunner {

    @Autowired
    private WebSocketService webSocketService;

    public static void main(String[] args) {
        SpringApplication.run(EstimateserverApplication.class, args);
        System.out.println("Server started");
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== SERVER STARTED ===");

        webSocketService.setServerJustStarted(true);

        System.out.println("⚠️ Restoring sessions and sending FORCE_LOGOUT...");

        webSocketService.forceLogoutAllActiveSessionsImmediately();

        Thread.sleep(5000);
        webSocketService.setServerJustStarted(false);

        System.out.println("✅ Server startup phase completed, accepting new connections");
    }
}
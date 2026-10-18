package com.newtonite.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Newtonite Ops.
 * Enables Spring Boot auto-configuration and scheduling for background outbox workers.
 */
@SpringBootApplication
@EnableScheduling
public class NewtoniteOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewtoniteOpsApplication.class, args);
    }
}

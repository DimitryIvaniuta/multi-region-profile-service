package com.github.dimitryivaniuta.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point for the multi-region profile service.
 *
 * <p>The service is intentionally deployable in two modes: a primary write region and read-only
 * regional replicas. Runtime properties decide the mode, which keeps one artifact deployable to
 * all regions while avoiding code drift.</p>
 */
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class ProfileServiceApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ProfileServiceApplication.class, args);
    }
}

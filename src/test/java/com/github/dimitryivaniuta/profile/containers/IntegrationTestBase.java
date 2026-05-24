package com.github.dimitryivaniuta.profile.containers;

import org.junit.jupiter.api.Disabled;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers base prepared for full integration tests in CI where Docker is available.
 *
 * <p>The sandbox used to generate this project has no Docker daemon, so concrete integration tests
 * can extend this class and remove the disabled annotation in a CI pipeline.</p>
 */
@Disabled("Enable in CI with Docker available")
public abstract class IntegrationTestBase {

    /** PostgreSQL container matching the production database family. */
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    /** Kafka container used for end-to-end event publication and consumption tests. */
    protected static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.0"));
}

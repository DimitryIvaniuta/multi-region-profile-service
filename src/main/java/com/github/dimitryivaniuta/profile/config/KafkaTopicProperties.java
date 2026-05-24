package com.github.dimitryivaniuta.profile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka topic and consumer switches for profile replication.
 *
 * @param profileChangesTopic compact event topic that carries profile snapshots
 * @param consumerEnabled enables regional read-model projection consumers
 */
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaTopicProperties(String profileChangesTopic, boolean consumerEnabled) {
}

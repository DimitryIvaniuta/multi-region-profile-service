package com.github.dimitryivaniuta.profile.config;

import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for local development and self-managed deployments.
 */
@Configuration
public class KafkaConfig {

    /**
     * Creates the profile change topic with log compaction enabled as a safety net for read-model
     * rebuild scenarios. Event consumers still process the full log by default.
     *
     * @param properties Kafka topic names configured for this service
     * @return topic definition for the profile changes stream
     */
    @Bean
    NewTopic profileChangesTopic(KafkaTopicProperties properties) {
        return TopicBuilder.name(properties.profileChangesTopic())
                .partitions(12)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT + "," + TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
                .build();
    }

    /**
     * Provides explicit idempotent producer defaults for environments that do not inject them.
     *
     * @return Kafka producer property defaults
     */
    @Bean
    Map<String, Object> kafkaProducerSafetyDefaults() {
        return Map.of(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.ACKS_CONFIG, "all"
        );
    }
}

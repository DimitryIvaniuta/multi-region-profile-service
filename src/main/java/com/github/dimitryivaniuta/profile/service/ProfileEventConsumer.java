package com.github.dimitryivaniuta.profile.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that feeds profile change events into the regional projection service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka", name = "consumer-enabled", havingValue = "true")
public class ProfileEventConsumer {

    private final ProfileProjectionService projectionService;

    /**
     * Handles one Kafka record. Blocking is intentional here because Spring Kafka invokes this
     * method on listener threads, not Netty event-loop threads.
     *
     * @param record consumed Kafka record including topic, partition and offset metadata
     */
    @KafkaListener(topics = "${app.kafka.profile-changes-topic}")
    public void onMessage(ConsumerRecord<String, String> record) {
        projectionService.apply(record.value(), record.topic(), record.partition(), record.offset(), record.key())
                .block(Duration.ofSeconds(15));
    }
}

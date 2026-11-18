package de.ibikus.appa.hephaestus.eventskafka.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/**
 * A real single-node Kafka 4.2 (KRaft). The share coordinator's state topic defaults to three replicas; on one
 * broker it must be one, or share groups cannot start.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainers {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.2.2")
                .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR", "1");
    }
}

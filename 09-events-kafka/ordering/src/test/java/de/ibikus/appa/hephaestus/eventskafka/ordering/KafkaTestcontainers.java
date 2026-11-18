package de.ibikus.appa.hephaestus.eventskafka.ordering;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/** A real single-node Kafka 4.2 (KRaft) for integration tests; Boot wires spring.kafka.bootstrap-servers. */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainers {

    public static final String IMAGE = "apache/kafka:4.2.2";

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer(IMAGE);
    }
}

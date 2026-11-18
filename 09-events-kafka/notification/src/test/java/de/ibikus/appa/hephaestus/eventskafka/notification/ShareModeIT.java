package de.ibikus.appa.hephaestus.eventskafka.notification;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.eventskafka.notification.application.service.NotificationService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * The share group mode on a real Kafka 4.2. A new share group starts at the latest offset, so the test keeps
 * sending until the member is assigned and the first notification arrives.
 */
@SpringBootTest(properties = "notification.mode=share")
@Import({PostgresTestcontainers.class, KafkaTestcontainers.class})
class ShareModeIT {

    @Autowired
    NotificationService service;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Test
    void shareGroupMemberHandlesEvents() {
        var customer = "share-" + UUID.randomUUID();

        var notified = KafkaProbe.await(Duration.ofSeconds(30), () -> {
            KafkaProbe.sendOrderPlaced(kafka, customer);
            return !service.forCustomer(customer).isEmpty();
        });

        assertThat(notified).isTrue();
    }
}

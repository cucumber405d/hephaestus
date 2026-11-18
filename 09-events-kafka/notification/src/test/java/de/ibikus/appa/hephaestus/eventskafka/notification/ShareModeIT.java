package de.ibikus.appa.hephaestus.eventskafka.notification;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.eventskafka.notification.application.service.Faults;
import de.ibikus.appa.hephaestus.eventskafka.notification.application.service.NotificationService;
import java.time.Duration;
import java.util.Set;
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

    @Test
    void failedRecordIsReleasedAndRedeliveredNotLost() {
        var customer = "share-retry-" + UUID.randomUUID();
        KafkaProbe.await(Duration.ofSeconds(30), () -> {
            KafkaProbe.sendOrderPlaced(kafka, "warm-" + UUID.randomUUID());
            return service.processed() > 0;
        });
        service.faults(new Faults(Set.of(), 1, 0));

        KafkaProbe.sendOrderPlaced(kafka, customer);

        assertThat(KafkaProbe.await(
                        Duration.ofSeconds(30),
                        () -> service.forCustomer(customer).size() == 1))
                .isTrue();
        service.faults(Faults.NONE);
    }
}

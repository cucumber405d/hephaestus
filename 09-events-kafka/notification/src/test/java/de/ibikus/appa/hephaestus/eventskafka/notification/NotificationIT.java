package de.ibikus.appa.hephaestus.eventskafka.notification;

import static de.ibikus.appa.hephaestus.eventskafka.notification.KafkaProbe.await;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging.DltReplayer;
import de.ibikus.appa.hephaestus.eventskafka.notification.application.service.Faults;
import de.ibikus.appa.hephaestus.eventskafka.notification.application.service.NotificationService;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

/** The classic consumer group on a real Kafka and PostgreSQL: happy path, duplicates, retries, DLT and replay. */
@SpringBootTest
@Import({PostgresTestcontainers.class, KafkaTestcontainers.class})
class NotificationIT {

    @Autowired
    NotificationService service;

    @Autowired
    DltReplayer replayer;

    @Autowired
    KafkaTemplate<String, String> kafka;

    /** The container itself: with @ServiceConnection the spring.kafka.bootstrap-servers property is not updated. */
    @Autowired
    KafkaContainer kafkaContainer;

    @AfterEach
    void healthy() {
        service.faults(Faults.NONE);
    }

    @Test
    void placedOrderIsNotified() {
        var customer = customer();

        KafkaProbe.sendOrderPlaced(kafka, customer);

        assertThat(await(Duration.ofSeconds(15), () -> service.forCustomer(customer).size() == 1)).isTrue();
    }

    @Test
    void duplicateEventNotifiesOnce() {
        var customer = customer();
        var eventId = UUID.randomUUID();
        var duplicatesBefore = service.duplicates();

        KafkaProbe.send(kafka, customer, eventId);
        KafkaProbe.send(kafka, customer, eventId);

        assertThat(await(Duration.ofSeconds(15), () -> service.duplicates() == duplicatesBefore + 1)).isTrue();
        assertThat(service.forCustomer(customer)).hasSize(1);
    }

    @Test
    void transientFailuresAreRetriedUntilTheySucceed() {
        var customer = customer();
        service.faults(new Faults(Set.of(), 2, 0));
        var started = System.nanoTime();

        KafkaProbe.sendOrderPlaced(kafka, customer);

        assertThat(await(Duration.ofSeconds(20), () -> service.forCustomer(customer).size() == 1)).isTrue();
        // two retries with 1 s and 2 s pauses before the success
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThan(Duration.ofSeconds(3));
        assertThat(KafkaProbe.read(kafkaContainer.getBootstrapServers(), "orders.events-dlt", customer)).isEmpty();
    }

    @Test
    void permanentFailureEndsInTheDltOthersKeepFlowingAndReplayDelivers() {
        var bob = customer();
        var alice = customer();
        service.faults(new Faults(Set.of(bob), 0, 0));

        KafkaProbe.sendOrderPlaced(kafka, bob);
        KafkaProbe.sendOrderPlaced(kafka, alice);

        assertThat(await(Duration.ofSeconds(30), () -> !KafkaProbe.read(kafkaContainer.getBootstrapServers(), "orders.events-dlt", bob)
                        .isEmpty()))
                .isTrue();
        assertThat(await(Duration.ofSeconds(15), () -> service.forCustomer(alice).size() == 1)).isTrue();
        assertThat(service.forCustomer(bob)).isEmpty();

        service.faults(Faults.NONE);
        assertThat(replayer.replay().replayed()).isGreaterThanOrEqualTo(1);

        assertThat(await(Duration.ofSeconds(15), () -> service.forCustomer(bob).size() == 1)).isTrue();
        assertThat(replayer.replay().replayed()).isZero();
    }

    @Test
    void poisonMessageGoesToTheDltWithoutRetries() {
        var key = customer();
        var started = System.nanoTime();

        KafkaProbe.sendRaw(kafka, key, "this is not json");

        assertThat(await(Duration.ofSeconds(15), () -> !KafkaProbe.read(kafkaContainer.getBootstrapServers(), "orders.events-dlt", key)
                        .isEmpty()))
                .isTrue();
        // retries would take at least 1 + 2 + 4 s
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(7));
        // replay skips it (sending it again cannot help) and returns instead of looping
        assertThat(replayer.replay().replayed()).isZero();
    }

    private static String customer() {
        return "c-" + UUID.randomUUID();
    }
}

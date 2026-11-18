package de.ibikus.appa.hephaestus.observability.delivery;

import static de.ibikus.appa.hephaestus.observability.delivery.Probe.await;
import static de.ibikus.appa.hephaestus.observability.delivery.Probe.message;
import static de.ibikus.appa.hephaestus.observability.delivery.Probe.read;
import static de.ibikus.appa.hephaestus.observability.delivery.Probe.types;
import static de.ibikus.appa.hephaestus.observability.delivery.messaging.SagaMessage.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.observability.delivery.admin.Faults;
import de.ibikus.appa.hephaestus.observability.delivery.admin.Levers;
import de.ibikus.appa.hephaestus.observability.delivery.deliveries.Deliveries;
import de.ibikus.appa.hephaestus.observability.delivery.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.delivery.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.observability.delivery.messaging.Topics;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

/** Delivery on real Kafka and PostgreSQL: scheduling, the injected failure, cancel before schedule. */
@SpringBootTest
@Import(Containers.class)
class DeliveryIT {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    Messages messages;

    @Autowired
    Deliveries deliveries;

    @Autowired
    Levers levers;

    @Autowired
    KafkaContainer container;

    @AfterEach
    void healthy() {
        levers.faults(Faults.NONE);
    }

    @Test
    void choreographySchedulesAfterStock() {
        var reserved = message(STOCK_RESERVED, CHOREOGRAPHY, "alice", "35.00", "HAMMER-01", 1);

        send(Topics.INVENTORY_EVENTS, reserved);

        assertThat(replies(reserved.orderId(), Topics.DELIVERY_EVENTS)).containsExactly(DELIVERY_SCHEDULED);
    }

    @Test
    void noCourierForTheCustomerFails() {
        levers.faults(new Faults(Set.of("dave"), 0));
        var command = message(SCHEDULE_DELIVERY, ORCHESTRATION, "dave", "35.00", "HAMMER-01", 1);

        send(Topics.DELIVERY_COMMANDS, command);

        assertThat(replies(command.orderId(), Topics.ORDERING_REPLIES)).containsExactly(DELIVERY_FAILED);
    }

    @Test
    void cancelBeforeScheduleWinsOverTheLateSchedule() {
        var schedule = message(SCHEDULE_DELIVERY, ORCHESTRATION, "alice", "35.00", "HAMMER-01", 1);

        send(Topics.DELIVERY_COMMANDS, schedule.next(CANCEL_DELIVERY, "timeout"));
        assertThat(await(
                        Duration.ofSeconds(15),
                        () -> deliveries.status(schedule.orderId()).isPresent()))
                .isTrue();
        send(Topics.DELIVERY_COMMANDS, schedule);

        assertThat(await(
                        Duration.ofSeconds(15),
                        () -> types(read(
                                        container.getBootstrapServers(),
                                        messages,
                                        Topics.ORDERING_REPLIES,
                                        schedule.orderId()))
                                .equals(List.of(DELIVERY_CANCELLED, DELIVERY_FAILED))))
                .isTrue();
        assertThat(deliveries.status(schedule.orderId())).contains("CANCELLED");
    }

    private void send(String topic, SagaMessage message) {
        Probe.send(kafka, messages, topic, message);
    }

    private List<String> replies(UUID orderId, String topic) {
        assertThat(await(
                        Duration.ofSeconds(20),
                        () -> !read(container.getBootstrapServers(), messages, topic, orderId)
                                .isEmpty()))
                .isTrue();
        return types(read(container.getBootstrapServers(), messages, topic, orderId));
    }
}

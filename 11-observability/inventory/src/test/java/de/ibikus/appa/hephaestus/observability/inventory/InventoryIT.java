package de.ibikus.appa.hephaestus.observability.inventory;

import static de.ibikus.appa.hephaestus.observability.inventory.Probe.await;
import static de.ibikus.appa.hephaestus.observability.inventory.Probe.message;
import static de.ibikus.appa.hephaestus.observability.inventory.Probe.read;
import static de.ibikus.appa.hephaestus.observability.inventory.Probe.types;
import static de.ibikus.appa.hephaestus.observability.inventory.messaging.SagaMessage.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.observability.inventory.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.inventory.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.observability.inventory.messaging.Topics;
import de.ibikus.appa.hephaestus.observability.inventory.stock.Stock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

/** Inventory on real Kafka and PostgreSQL: both styles, all-or-nothing, release once. */
@SpringBootTest
@Import(Containers.class)
class InventoryIT {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    Messages messages;

    @Autowired
    Stock stock;

    @Autowired
    KafkaContainer container;

    @Test
    void choreographyReservesAfterPayment() {
        var paid = message(PAYMENT_RESERVED, CHOREOGRAPHY, "alice", "35.00", "HAMMER-01", 1);

        send(Topics.PAYMENT_EVENTS, paid);

        assertThat(replies(paid.orderId(), Topics.INVENTORY_EVENTS)).containsExactly(STOCK_RESERVED);
    }

    @Test
    void missingStockIsUnavailable() {
        var command = message(RESERVE_STOCK, ORCHESTRATION, "alice", "18.50", "TONGS-01", 1);

        send(Topics.INVENTORY_COMMANDS, command);

        assertThat(replies(command.orderId(), Topics.ORDERING_REPLIES)).containsExactly(STOCK_UNAVAILABLE);
    }

    @Test
    void reservationAndReleaseAreBothOnce() {
        var command = message(RESERVE_STOCK, ORCHESTRATION, "alice", "35.00", "HAMMER-01", 2);
        var before = stock.reserved("HAMMER-01");

        send(Topics.INVENTORY_COMMANDS, command);
        send(Topics.INVENTORY_COMMANDS, command);
        assertThat(replies(command.orderId(), Topics.ORDERING_REPLIES)).containsExactly(STOCK_RESERVED);
        assertThat(stock.reserved("HAMMER-01")).isEqualTo(before + 2);

        send(Topics.INVENTORY_COMMANDS, command.next(RELEASE_STOCK, "test"));
        send(Topics.INVENTORY_COMMANDS, command.next(RELEASE_STOCK, "test again"));

        assertThat(await(Duration.ofSeconds(15), () -> stock.reserved("HAMMER-01") == before))
                .isTrue();
        assertThat(await(
                        Duration.ofSeconds(20),
                        () -> types(read(
                                        container.getBootstrapServers(),
                                        messages,
                                        Topics.ORDERING_REPLIES,
                                        command.orderId()))
                                .equals(List.of(STOCK_RESERVED, STOCK_RELEASED, STOCK_RELEASED))))
                .isTrue();
    }

    @Test
    void choreographyReleasesWhenDeliveryFails() {
        var paid = message(PAYMENT_RESERVED, CHOREOGRAPHY, "alice", "35.00", "HAMMER-01", 1);
        send(Topics.PAYMENT_EVENTS, paid);
        replies(paid.orderId(), Topics.INVENTORY_EVENTS);

        send(Topics.DELIVERY_EVENTS, paid.next(DELIVERY_FAILED, "no courier"));

        assertThat(await(
                        Duration.ofSeconds(15),
                        () -> types(read(
                                        container.getBootstrapServers(),
                                        messages,
                                        Topics.INVENTORY_EVENTS,
                                        paid.orderId()))
                                .equals(List.of(STOCK_RESERVED, STOCK_RELEASED))))
                .isTrue();
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

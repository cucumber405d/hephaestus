package de.ibikus.appa.hephaestus.outboxsaga.ordering;

import static de.ibikus.appa.hephaestus.outboxsaga.ordering.Probe.await;
import static de.ibikus.appa.hephaestus.outboxsaga.ordering.Probe.read;
import static de.ibikus.appa.hephaestus.outboxsaga.ordering.Probe.types;
import static de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.SagaMessage.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.Messages;
import de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.Topics;
import de.ibikus.appa.hephaestus.outboxsaga.ordering.orders.Orders;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Ordering on real Kafka and PostgreSQL, the test playing the participants: outbox, both saga styles, the step
 * timeout (3 s here).
 */
@SpringBootTest(properties = "saga.step-timeout=3s")
@AutoConfigureMockMvc
@Import(Containers.class)
class OrderingIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    Messages messages;

    @Autowired
    Orders orders;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer container;

    @Test
    void choreographyOrderIsPublishedThroughTheOutboxAndTracked() {
        var id = place("choreography", "ANVIL-01", 2);

        var placed = awaitOne(Topics.ORDERING_EVENTS, id);
        assertThat(placed.type()).isEqualTo(ORDER_PLACED);
        assertThat(placed.amount()).isEqualByComparingTo("240.00");
        assertThat(jdbc.sql("select count(*) from event_publication").query(Long.class).single()).isPositive();

        send(Topics.PAYMENT_EVENTS, placed.next(PAYMENT_RESERVED, null));
        send(Topics.INVENTORY_EVENTS, placed.next(STOCK_RESERVED, null));
        send(Topics.DELIVERY_EVENTS, placed.next(DELIVERY_SCHEDULED, null));

        assertThat(await(Duration.ofSeconds(15), () -> status(id).equals("CONFIRMED"))).isTrue();
    }

    @Test
    void orchestrationSendsTheNextCommandForEachReply() {
        var id = place("orchestration", "HAMMER-01", 1);

        var reserve = awaitOne(Topics.PAYMENT_COMMANDS, id);
        assertThat(reserve.type()).isEqualTo(RESERVE_PAYMENT);
        send(Topics.ORDERING_REPLIES, reserve.next(PAYMENT_RESERVED, null));

        var reserveStock = awaitOne(Topics.INVENTORY_COMMANDS, id);
        assertThat(reserveStock.type()).isEqualTo(RESERVE_STOCK);
        send(Topics.ORDERING_REPLIES, reserve.next(STOCK_UNAVAILABLE, "not enough stock"));

        assertThat(await(Duration.ofSeconds(15), () -> status(id).equals("REJECTED"))).isTrue();
        assertThat(await(Duration.ofSeconds(15), () -> types(read(
                                container.getBootstrapServers(), messages, Topics.PAYMENT_COMMANDS, id))
                        .equals(List.of(RESERVE_PAYMENT, REFUND_PAYMENT))))
                .isTrue();
    }

    @Test
    void silentParticipantTimesOutAndIsCompensated() {
        var id = place("orchestration", "HAMMER-01", 1);
        var reserve = awaitOne(Topics.PAYMENT_COMMANDS, id);
        send(Topics.ORDERING_REPLIES, reserve.next(PAYMENT_RESERVED, null));
        send(Topics.ORDERING_REPLIES, reserve.next(STOCK_RESERVED, null));
        // Delivery never answers

        assertThat(await(Duration.ofSeconds(20), () -> status(id).equals("REJECTED"))).isTrue();
        assertThat(await(Duration.ofSeconds(15), () -> types(read(
                                container.getBootstrapServers(), messages, Topics.DELIVERY_COMMANDS, id))
                        .equals(List.of(SCHEDULE_DELIVERY, CANCEL_DELIVERY))))
                .isTrue();
        assertThat(orders.find(id).orElseThrow().reason()).isEqualTo("timeout at step delivery");
    }

    @Test
    void duplicateReplyIsIgnored() {
        var id = place("orchestration", "HAMMER-01", 1);
        var reserve = awaitOne(Topics.PAYMENT_COMMANDS, id);
        var reply = reserve.next(PAYMENT_RESERVED, null);

        send(Topics.ORDERING_REPLIES, reply);
        send(Topics.ORDERING_REPLIES, reply);

        awaitOne(Topics.INVENTORY_COMMANDS, id);
        assertThat(read(container.getBootstrapServers(), messages, Topics.INVENTORY_COMMANDS, id)).hasSize(1);
    }

    private UUID place(String saga, String sku, int quantity) {
        var result = mvc.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":"alice","saga":"%s","lines":[{"sku":"%s","quantity":%d}]}"""
                        .formatted(saga, sku, quantity))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        return UUID.fromString(result.getResponse().getHeader("Location").replaceAll(".*/", ""));
    }

    private String status(UUID id) {
        return orders.find(id).orElseThrow().status();
    }

    private SagaMessage awaitOne(String topic, UUID orderId) {
        assertThat(await(Duration.ofSeconds(20), () -> !read(container.getBootstrapServers(), messages, topic, orderId)
                        .isEmpty()))
                .isTrue();
        return read(container.getBootstrapServers(), messages, topic, orderId).getFirst();
    }

    private void send(String topic, SagaMessage message) {
        Probe.send(kafka, messages, topic, message);
    }
}

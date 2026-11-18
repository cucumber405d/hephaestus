package de.ibikus.appa.hephaestus.observability.ordering;

import static de.ibikus.appa.hephaestus.observability.ordering.Probe.await;
import static de.ibikus.appa.hephaestus.observability.ordering.Probe.read;
import static de.ibikus.appa.hephaestus.observability.ordering.Probe.readWithTrace;
import static de.ibikus.appa.hephaestus.observability.ordering.Probe.types;
import static de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.observability.ordering.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.Topics;
import de.ibikus.appa.hephaestus.observability.ordering.orders.Orders;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
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
 * timeout (10 s here: well above the time the test needs to read a topic and answer).
 */
@SpringBootTest(properties = "saga.step-timeout=10s")
@AutoConfigureTracing
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
        assertThat(jdbc.sql("select count(*) from event_publication")
                        .query(Long.class)
                        .single())
                .isPositive();

        send(Topics.PAYMENT_EVENTS, placed.next(PAYMENT_RESERVED, null));
        send(Topics.INVENTORY_EVENTS, placed.next(STOCK_RESERVED, null));
        send(Topics.DELIVERY_EVENTS, placed.next(DELIVERY_SCHEDULED, null));

        assertThat(await(Duration.ofSeconds(15), () -> status(id).equals("CONFIRMED")))
                .isTrue();
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

        assertThat(await(Duration.ofSeconds(15), () -> status(id).equals("REJECTED")))
                .isTrue();
        assertThat(await(
                        Duration.ofSeconds(15),
                        () -> types(read(container.getBootstrapServers(), messages, Topics.PAYMENT_COMMANDS, id))
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

        assertThat(await(Duration.ofSeconds(40), () -> status(id).equals("REJECTED")))
                .isTrue();
        assertThat(await(
                        Duration.ofSeconds(15),
                        () -> types(read(container.getBootstrapServers(), messages, Topics.DELIVERY_COMMANDS, id))
                                .equals(List.of(SCHEDULE_DELIVERY, CANCEL_DELIVERY))))
                .isTrue();
        assertThat(orders.find(id).orElseThrow().reason()).isEqualTo("timeout at step delivery");
    }

    @Test
    void orderPlacedContinuesTheTraceOfTheRequest() {
        var id = place("choreography", "ANVIL-01", 1, "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        awaitOne(Topics.ORDERING_EVENTS, id);
        assertThat(readWithTrace(container.getBootstrapServers(), messages, Topics.ORDERING_EVENTS, id)
                        .getFirst()
                        .traceId())
                .isEqualTo("0af7651916cd43dd8448eb211c80319c");
    }

    @Test
    void timeoutCompensationsStayInTheSagaTrace() {
        var id = place("orchestration", "HAMMER-01", 1);
        var reserve = awaitOne(Topics.PAYMENT_COMMANDS, id);
        send(Topics.ORDERING_REPLIES, reserve.next(PAYMENT_RESERVED, null));
        send(Topics.ORDERING_REPLIES, reserve.next(STOCK_RESERVED, null));
        // Delivery never answers: the scheduler times the step out and compensates

        assertThat(await(Duration.ofSeconds(40), () -> status(id).equals("REJECTED")))
                .isTrue();
        assertThat(await(
                        Duration.ofSeconds(15),
                        () -> readWithTrace(container.getBootstrapServers(), messages, Topics.DELIVERY_COMMANDS, id)
                                        .size()
                                == 2))
                .isTrue();
        var delivery = readWithTrace(container.getBootstrapServers(), messages, Topics.DELIVERY_COMMANDS, id);
        assertThat(delivery.get(0).message().type()).isEqualTo(SCHEDULE_DELIVERY);
        assertThat(delivery.get(1).message().type()).isEqualTo(CANCEL_DELIVERY);
        // The cancel was sent by the scheduler, yet it is in the trace of the step it cancels
        assertThat(delivery.get(1).traceId())
                .isNotNull()
                .isEqualTo(delivery.get(0).traceId());
    }

    @Test
    void duplicateReplyIsIgnored() {
        var id = place("orchestration", "HAMMER-01", 1);
        var reserve = awaitOne(Topics.PAYMENT_COMMANDS, id);
        var reply = reserve.next(PAYMENT_RESERVED, null);

        send(Topics.ORDERING_REPLIES, reply);
        send(Topics.ORDERING_REPLIES, reply);

        awaitOne(Topics.INVENTORY_COMMANDS, id);
        assertThat(read(container.getBootstrapServers(), messages, Topics.INVENTORY_COMMANDS, id))
                .hasSize(1);
    }

    private UUID place(String saga, String sku, int quantity) {
        return place(saga, sku, quantity, null);
    }

    /** Places an order; with a traceparent the request comes in as part of that trace (as from a traced caller). */
    private UUID place(String saga, String sku, int quantity, String traceparent) {
        var request = mvc.post().uri("/api/orders");
        if (traceparent != null) {
            request = request.header("traceparent", traceparent);
        }
        var result = request.contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":"alice","saga":"%s","lines":[{"sku":"%s","quantity":%d}]}""".formatted(saga, sku, quantity))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        return UUID.fromString(result.getResponse().getHeader("Location").replaceAll(".*/", ""));
    }

    private String status(UUID id) {
        return orders.find(id).orElseThrow().status();
    }

    /** The first message about the order on the topic, from the read that found it (no second read). */
    private SagaMessage awaitOne(String topic, UUID orderId) {
        var found = new java.util.concurrent.atomic.AtomicReference<SagaMessage>();
        assertThat(await(Duration.ofSeconds(20), () -> {
                    var messagesFound = read(container.getBootstrapServers(), messages, topic, orderId);
                    if (!messagesFound.isEmpty()) {
                        found.set(messagesFound.getFirst());
                    }
                    return found.get() != null;
                }))
                .isTrue();
        return found.get();
    }

    private void send(String topic, SagaMessage message) {
        Probe.send(kafka, messages, topic, message);
    }
}

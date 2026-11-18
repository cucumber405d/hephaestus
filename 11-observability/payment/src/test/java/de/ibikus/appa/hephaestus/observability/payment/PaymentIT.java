package de.ibikus.appa.hephaestus.observability.payment;

import static de.ibikus.appa.hephaestus.observability.payment.Probe.await;
import static de.ibikus.appa.hephaestus.observability.payment.Probe.message;
import static de.ibikus.appa.hephaestus.observability.payment.Probe.read;
import static de.ibikus.appa.hephaestus.observability.payment.Probe.readWithTrace;
import static de.ibikus.appa.hephaestus.observability.payment.Probe.types;
import static de.ibikus.appa.hephaestus.observability.payment.messaging.SagaMessage.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.observability.payment.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.payment.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.observability.payment.messaging.Topics;
import de.ibikus.appa.hephaestus.observability.payment.payments.Payments;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

/** Payment on real Kafka and PostgreSQL: both styles, idempotency, compensation, outbox. */
@SpringBootTest
@AutoConfigureTracing
@Import(Containers.class)
class PaymentIT {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    Messages messages;

    @Autowired
    Payments payments;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer container;

    @Test
    void orchestrationCommandIsAnsweredOnTheRepliesTopic() {
        var command = message(RESERVE_PAYMENT, ORCHESTRATION, customer(), "120.00", "ANVIL-01", 1);

        send(Topics.PAYMENT_COMMANDS, command);

        assertThat(replies(command.orderId(), Topics.ORDERING_REPLIES)).containsExactly(PAYMENT_RESERVED);
    }

    @Test
    void repliesCarryATraceparent() {
        var command = message(RESERVE_PAYMENT, ORCHESTRATION, customer(), "120.00", "ANVIL-01", 1);

        send(Topics.PAYMENT_COMMANDS, command);

        assertThat(await(
                        Duration.ofSeconds(20),
                        () -> !readWithTrace(
                                        container.getBootstrapServers(),
                                        messages,
                                        Topics.ORDERING_REPLIES,
                                        command.orderId())
                                .isEmpty()))
                .isTrue();
        assertThat(readWithTrace(container.getBootstrapServers(), messages, Topics.ORDERING_REPLIES, command.orderId())
                        .getFirst()
                        .traceId())
                .matches("[0-9a-f]{32}");
    }

    @Test
    void choreographyReactsToOrderPlacedWithItsOwnEvent() {
        var placed = message(ORDER_PLACED, CHOREOGRAPHY, customer(), "35.00", "HAMMER-01", 1);

        send(Topics.ORDERING_EVENTS, placed);

        assertThat(replies(placed.orderId(), Topics.PAYMENT_EVENTS)).containsExactly(PAYMENT_RESERVED);
    }

    @Test
    void overTheLimitIsDeclined() {
        var command = message(RESERVE_PAYMENT, ORCHESTRATION, "bob", "120.00", "ANVIL-01", 1);

        send(Topics.PAYMENT_COMMANDS, command);

        assertThat(replies(command.orderId(), Topics.ORDERING_REPLIES)).containsExactly(PAYMENT_DECLINED);
    }

    @Test
    void sameCommandTwiceReservesOnce() {
        var customer = customer();
        var command = message(RESERVE_PAYMENT, ORCHESTRATION, customer, "100.00", "ANVIL-01", 1);

        send(Topics.PAYMENT_COMMANDS, command);
        send(Topics.PAYMENT_COMMANDS, command);

        assertThat(replies(command.orderId(), Topics.ORDERING_REPLIES)).containsExactly(PAYMENT_RESERVED);
        assertThat(payments.reserved(customer)).isEqualByComparingTo("100.00");
    }

    @Test
    void refundGivesTheMoneyBackOnceAndARefundWithoutReservationIsHarmless() {
        var customer = customer();
        var reserve = message(RESERVE_PAYMENT, ORCHESTRATION, customer, "100.00", "ANVIL-01", 1);
        send(Topics.PAYMENT_COMMANDS, reserve);
        assertThat(await(
                        Duration.ofSeconds(15),
                        () -> payments.reserved(customer).signum() > 0))
                .isTrue();

        send(Topics.PAYMENT_COMMANDS, reserve.next(REFUND_PAYMENT, "test"));
        send(Topics.PAYMENT_COMMANDS, reserve.next(REFUND_PAYMENT, "test again"));

        assertThat(await(
                        Duration.ofSeconds(15),
                        () -> payments.reserved(customer).signum() == 0))
                .isTrue();
        assertThat(await(
                        Duration.ofSeconds(20),
                        () -> types(read(
                                        container.getBootstrapServers(),
                                        messages,
                                        Topics.ORDERING_REPLIES,
                                        reserve.orderId()))
                                .equals(java.util.List.of(PAYMENT_RESERVED, PAYMENT_REFUNDED, PAYMENT_REFUNDED))))
                .isTrue();
        assertThat(payments.reserved(customer)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void refundBeforeReservationMakesALateReservationFail() {
        var reserve = message(RESERVE_PAYMENT, ORCHESTRATION, customer(), "10.00", "ANVIL-01", 1);

        send(Topics.PAYMENT_COMMANDS, reserve.next(REFUND_PAYMENT, "timeout"));
        assertThat(await(
                        Duration.ofSeconds(15),
                        () -> payments.status(reserve.orderId()).isPresent()))
                .isTrue();
        send(Topics.PAYMENT_COMMANDS, reserve);

        assertThat(await(
                        Duration.ofSeconds(20),
                        () -> types(read(
                                        container.getBootstrapServers(),
                                        messages,
                                        Topics.ORDERING_REPLIES,
                                        reserve.orderId()))
                                .equals(java.util.List.of(PAYMENT_REFUNDED, PAYMENT_DECLINED))))
                .isTrue();
    }

    @Test
    void messageOfTheOtherStyleIsIgnored() {
        var placed = message(ORDER_PLACED, ORCHESTRATION, customer(), "35.00", "HAMMER-01", 1);

        send(Topics.ORDERING_EVENTS, placed);

        assertThat(read(container.getBootstrapServers(), messages, Topics.PAYMENT_EVENTS, placed.orderId()))
                .isEmpty();
        assertThat(payments.status(placed.orderId())).isEmpty();
    }

    @Test
    void outgoingMessageWentThroughTheOutboxTable() {
        var command = message(RESERVE_PAYMENT, ORCHESTRATION, customer(), "1.00", "ANVIL-01", 1);

        send(Topics.PAYMENT_COMMANDS, command);
        replies(command.orderId(), Topics.ORDERING_REPLIES);

        var completed = jdbc.sql("select count(*) from event_publication where completion_date is not null")
                .query(Long.class)
                .single();
        assertThat(completed).isPositive();
    }

    private void send(String topic, SagaMessage message) {
        Probe.send(kafka, messages, topic, message);
    }

    /** Waits for at least one message about the order on the topic and returns their types. */
    private java.util.List<String> replies(UUID orderId, String topic) {
        assertThat(await(
                        Duration.ofSeconds(20),
                        () -> !read(container.getBootstrapServers(), messages, topic, orderId)
                                .isEmpty()))
                .isTrue();
        return types(read(container.getBootstrapServers(), messages, topic, orderId));
    }

    private static String customer() {
        return "c-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

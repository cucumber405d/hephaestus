package de.ibikus.appa.hephaestus.observability.ordering.messaging;

import static de.ibikus.appa.hephaestus.observability.ordering.Probe.await;
import static de.ibikus.appa.hephaestus.observability.ordering.Probe.message;
import static de.ibikus.appa.hephaestus.observability.ordering.Probe.readWithTrace;
import static de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.observability.ordering.Containers;
import de.ibikus.appa.hephaestus.observability.ordering.orders.OrderService;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.kafka.KafkaContainer;

/** The relay sends in the trace stored with the message: right away, and after Kafka was down. */
@SpringBootTest
@AutoConfigureTracing
@Import(Containers.class)
class OutboxRelayIT {

    static final String PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Autowired
    OutboxRelay relay;

    @Autowired
    OrderService orders;

    @Autowired
    Tracer tracer;

    @Autowired
    Messages messages;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer container;

    @Test
    void relayedMessageCarriesTheStoredTrace() {
        var command = message(RESERVE_PAYMENT, ORCHESTRATION, "alice", "35.00", "HAMMER-01", 1);

        relay.relay(new OutgoingMessage(Topics.PAYMENT_COMMANDS, command, PARENT));

        assertThat(await(
                        Duration.ofSeconds(20),
                        () -> !readWithTrace(
                                        container.getBootstrapServers(),
                                        messages,
                                        Topics.PAYMENT_COMMANDS,
                                        command.orderId())
                                .isEmpty()))
                .isTrue();
        assertThat(readWithTrace(container.getBootstrapServers(), messages, Topics.PAYMENT_COMMANDS, command.orderId())
                        .getFirst()
                        .traceId())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void resubmittedPublicationKeepsTheTrace() {
        var docker = container.getDockerClient();
        docker.pauseContainerCmd(container.getContainerId()).exec();
        java.util.UUID orderId;
        String traceId;
        var span = tracer.nextSpan().name("test order").start();
        try (var scope = tracer.withSpan(span)) {
            traceId = span.context().traceId();
            orderId = orders.place("alice", ORCHESTRATION, List.of(new Line("HAMMER-01", 1)))
                    .id();
        } finally {
            span.end();
        }
        try {
            // Kafka is away: the first send fails after max.block.ms and the publication stays incomplete
            assertThat(await(Duration.ofSeconds(15), () -> incomplete() > 0)).isTrue();
        } finally {
            docker.unpauseContainerCmd(container.getContainerId()).exec();
        }

        assertThat(await(
                        Duration.ofSeconds(60),
                        () -> !readWithTrace(
                                        container.getBootstrapServers(), messages, Topics.PAYMENT_COMMANDS, orderId)
                                .isEmpty()))
                .isTrue();
        assertThat(readWithTrace(container.getBootstrapServers(), messages, Topics.PAYMENT_COMMANDS, orderId)
                        .getFirst()
                        .traceId())
                .isEqualTo(traceId);
        assertThat(await(Duration.ofSeconds(20), () -> incomplete() == 0)).isTrue();
    }

    private long incomplete() {
        return jdbc.sql("select count(*) from event_publication where completion_date is null")
                .query(Long.class)
                .single();
    }
}

package de.ibikus.appa.hephaestus.observability.ordering.messaging;

import static de.ibikus.appa.hephaestus.observability.ordering.Probe.await;
import static de.ibikus.appa.hephaestus.observability.ordering.Probe.message;
import static de.ibikus.appa.hephaestus.observability.ordering.Probe.readWithTrace;
import static de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage.*;
import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.observability.ordering.Containers;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

/** Carrying the trace switched off: the stored traceparent is ignored and the send starts its own trace. */
@SpringBootTest(properties = "observability.outbox.carry-trace=false")
@AutoConfigureTracing
@Import(Containers.class)
class OutboxRelaySwitchedOffIT {

    @Autowired
    OutboxRelay relay;

    @Autowired
    Messages messages;

    @Autowired
    KafkaContainer container;

    @Test
    void switchedOffStartsANewTrace() {
        var command = message(RESERVE_PAYMENT, ORCHESTRATION, "alice", "35.00", "HAMMER-01", 1);

        relay.relay(new OutgoingMessage(Topics.PAYMENT_COMMANDS, command, OutboxRelayIT.PARENT));

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
                .isNotEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }
}

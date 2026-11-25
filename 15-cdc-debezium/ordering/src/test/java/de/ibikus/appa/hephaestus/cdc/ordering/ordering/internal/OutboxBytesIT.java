package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.cdc.ordering.Containers;
import de.ibikus.appa.hephaestus.cdc.ordering.KafkaProbe;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The anti-goal of 15: a record from Debezium must not differ by one byte from the relay's. Same event, two paths —
 * the relay's KafkaTemplate (value read back raw) and the outbox serializer — compared byte for byte, schema id prefix
 * included.
 */
@SpringBootTest
@Import(Containers.class)
class OutboxBytesIT {

    @Autowired
    OutboxSerializer outbox;

    @Autowired
    KafkaTemplate<String, Object> kafka;

    @Autowired
    KafkaContainer broker;

    @Test
    void outboxBytesEqualRelayBytes() throws Exception {
        var event = new WorkOrderRequestedEvent(UUID.randomUUID(), "IRON-01", 5, "q-1", 6000);
        var key = event.orderId().toString();

        kafka.send(WorkOrderRequests.TOPIC, key, WorkOrderRequests.toAvro(event))
                .get(10, TimeUnit.SECONDS);
        var relayBytes = KafkaProbe.readBytes(
                        broker.getBootstrapServers(), WorkOrderRequests.TOPIC, key, Duration.ofSeconds(15))
                .orElseThrow();

        assertThat(outbox.serialize(WorkOrderRequests.toAvro(event))).isEqualTo(relayBytes);
    }
}

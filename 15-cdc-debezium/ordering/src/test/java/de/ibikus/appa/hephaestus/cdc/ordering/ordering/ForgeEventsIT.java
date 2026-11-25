package de.ibikus.appa.hephaestus.cdc.ordering.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import de.ibikus.appa.hephaestus.contracts.events.ForgeEvent;
import de.ibikus.appa.hephaestus.contracts.events.ForgeEventType;
import de.ibikus.appa.hephaestus.cdc.ordering.AvroProbe;
import de.ibikus.appa.hephaestus.cdc.ordering.Containers;
import de.ibikus.appa.hephaestus.cdc.ordering.KafkaProbe;
import de.ibikus.appa.hephaestus.cdc.ordering.SchemaRegistry;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal.QuoteClient;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal.QuoteClient.Quoted;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/** forge-service's outcomes arrive from Kafka as Avro (the test plays the service): applied once per eventId. */
@SpringBootTest
@Import(Containers.class)
class ForgeEventsIT {

    static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    Orders orders;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    GenericContainer<?> apicurio;

    @MockitoBean
    QuoteClient quotes;

    @BeforeEach
    void quoted() {
        doReturn(new Quoted(UUID.randomUUID().toString(), 1200, 3)).when(quotes).quote(anyString(), anyInt());
    }

    @Test
    void completedEventMakesOrderReady() {
        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 1));

        send(UUID.randomUUID(), ForgeEventType.WORK_ORDER_COMPLETED, id, null);

        await().atMost(WAIT).until(() -> orders.get(id).status(), "READY"::equals);
    }

    @Test
    void failedEventRejectsWithReason() {
        var id = orders.place(new OrderRequest("c1", null, true, "MITHRIL-01", 1));

        send(UUID.randomUUID(), ForgeEventType.WORK_ORDER_FAILED, id, "material not deliverable: MITHRIL-01");

        await().atMost(WAIT).until(() -> orders.get(id).status(), "REJECTED"::equals);
        assertThat(orders.get(id).reason()).isEqualTo("material not deliverable: MITHRIL-01");
    }

    @Test
    void duplicateCompletionIsIgnored() {
        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 1));
        var eventId = UUID.randomUUID();
        send(eventId, ForgeEventType.WORK_ORDER_COMPLETED, id, null);
        send(eventId, ForgeEventType.WORK_ORDER_COMPLETED, id, null);
        // a later, different event for the same order must not overturn it either
        var late = UUID.randomUUID();
        send(late, ForgeEventType.WORK_ORDER_FAILED, id, "late");

        await().atMost(WAIT).until(() -> processed(late) == 1);
        assertThat(orders.get(id).status()).isEqualTo("READY");
        assertThat(processed(eventId)).isEqualTo(1);
    }

    @Test
    void poisonRecordGoesToDlt() {
        // not Avro: parked on cdc.forge.events.DLT with its bytes, not retried for ever and not silently skipped
        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 1));

        KafkaProbe.send(kafka.getBootstrapServers(), "cdc.forge.events", id.toString(), "not avro");

        assertThat(KafkaProbe.read(kafka.getBootstrapServers(), "cdc.forge.events.DLT", id.toString(), WAIT))
                .hasValue("not avro");
        assertThat(orders.get(id).status()).isEqualTo("ACCEPTED");
    }

    @Test
    void malformedEventIdGoesToDlt() {
        // readable Avro, but the listener refuses it (IllegalArgumentException, not retried): parked as JSON text
        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 1));
        var event = ForgeEvent.newBuilder()
                .setEventId("not-a-uuid")
                .setType(ForgeEventType.WORK_ORDER_COMPLETED)
                .setOrderId(id.toString())
                .setWorkOrderId("FW-x")
                .build();
        AvroProbe.send(
                kafka.getBootstrapServers(),
                Containers.url(apicurio),
                "cdc.forge.events",
                id.toString(),
                event,
                SchemaRegistry.CURRENT);

        assertThat(KafkaProbe.read(kafka.getBootstrapServers(), "cdc.forge.events.DLT", id.toString(), WAIT))
                .hasValueSatisfying(text -> assertThat(text).contains("not-a-uuid"));
        assertThat(orders.get(id).status()).isEqualTo("ACCEPTED");
    }

    private void send(UUID eventId, ForgeEventType type, UUID orderId, String reason) {
        var event = ForgeEvent.newBuilder()
                .setEventId(eventId.toString())
                .setType(type)
                .setOrderId(orderId.toString())
                .setWorkOrderId("FW-" + UUID.randomUUID())
                .setReason(reason)
                .build();
        AvroProbe.send(
                kafka.getBootstrapServers(),
                Containers.url(apicurio),
                "cdc.forge.events",
                orderId.toString(),
                event,
                SchemaRegistry.CURRENT);
    }

    private int processed(UUID eventId) {
        return jdbc.sql("select count(*) from ordering.processed_event where event_id = ?")
                .param(eventId)
                .query(Integer.class)
                .single();
    }
}

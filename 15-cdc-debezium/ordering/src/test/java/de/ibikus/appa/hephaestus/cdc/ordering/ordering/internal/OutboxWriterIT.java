package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import de.ibikus.appa.hephaestus.cdc.ordering.AvroProbe;
import de.ibikus.appa.hephaestus.cdc.ordering.Containers;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.OrderRequest;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.Orders;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal.QuoteClient.Quoted;
import de.ibikus.appa.hephaestus.contracts.events.WorkOrderRequest;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/** Debezium mode: the quoted order writes its request to ordering.outbox in the same transaction; nothing is sent. */
@SpringBootTest
@Import(Containers.class)
class OutboxWriterIT {

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
        doReturn(new Quoted("q-1", 6000, 3)).when(quotes).quote(anyString(), anyInt());
    }

    @Test
    void quotedOrderWritesOneOutboxRow() {
        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 5));

        var rows = jdbc.sql("""
                        select aggregate_type, aggregate_id, type, payload from ordering.outbox
                        where aggregate_id = ?""")
                .param(id.toString())
                .query((rs, n) -> Map.<String, Object>of(
                        "aggregate_type", rs.getString(1),
                        "type", rs.getString(3),
                        "payload", rs.getBytes(4)))
                .list();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("aggregate_type", "forge.requests")
                .containsEntry("type", "WorkOrderRequest");

        var request = deserialize((byte[]) rows.getFirst().get("payload"));
        assertThat(request.getOrderId()).isEqualTo(id.toString());
        assertThat(request.getQuoteId()).isEqualTo("q-1");
        assertThat(request.getPriceCents()).isEqualTo(6000L);
        // nothing leaves the service itself: only Kafka Connect publishes in this mode
        assertThat(AvroProbe.read(
                        kafka.getBootstrapServers(),
                        Containers.url(apicurio),
                        WorkOrderRequests.TOPIC,
                        id.toString(),
                        Duration.ofSeconds(3)))
                .isEmpty();
    }

    private WorkOrderRequest deserialize(byte[] payload) {
        try (var deserializer = new AvroKafkaDeserializer<WorkOrderRequest>()) {
            deserializer.configure(
                    Map.of(
                            "apicurio.registry.url", Containers.url(apicurio) + "/apis/registry/v3",
                            "apicurio.registry.use-specific-avro-reader", true,
                            "apicurio.registry.headers.enabled", false),
                    false);
            return deserializer.deserialize(WorkOrderRequests.TOPIC, payload);
        }
    }
}

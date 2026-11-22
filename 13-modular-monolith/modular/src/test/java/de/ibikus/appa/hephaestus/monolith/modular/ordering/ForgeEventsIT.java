package de.ibikus.appa.hephaestus.monolith.modular.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.monolith.modular.Containers;
import de.ibikus.appa.hephaestus.monolith.modular.FakeSupplier;
import de.ibikus.appa.hephaestus.monolith.modular.KafkaProbe;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/** forge-service's outcomes arrive from Kafka (the test plays the service): applied once per eventId. */
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

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @BeforeEach
    void cutOver() {
        jdbc.sql("update migration.forge_cutover set remote = true").update();
    }

    @AfterEach
    void back() {
        jdbc.sql("update migration.forge_cutover set remote = false").update();
    }

    @Test
    void completionFromKafkaMakesTheOrderReady() {
        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 1));
        send(UUID.randomUUID(), "WorkOrderCompleted", id, null);
        await().atMost(WAIT).until(() -> orders.get(id).status(), "READY"::equals);
    }

    @Test
    void failureFromKafkaRejectsTheOrder() {
        var id = orders.place(new OrderRequest("c1", null, true, "MITHRIL-01", 1));
        send(UUID.randomUUID(), "WorkOrderFailed", id, "material not deliverable: MITHRIL-01");
        await().atMost(WAIT).until(() -> orders.get(id).status(), "REJECTED"::equals);
        assertThat(orders.get(id).reason()).isEqualTo("material not deliverable: MITHRIL-01");
    }

    @Test
    void duplicateCompletionIsIgnored() {
        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 1));
        var eventId = UUID.randomUUID();
        send(eventId, "WorkOrderCompleted", id, null);
        send(eventId, "WorkOrderCompleted", id, null);
        // a later, different event for the same order must not overturn it either
        var late = UUID.randomUUID();
        send(late, "WorkOrderFailed", id, "late");
        await().atMost(WAIT)
                .until(() -> jdbc.sql("select count(*) from ordering.processed_event where event_id = ?")
                                .param(late)
                                .query(Integer.class)
                                .single()
                        == 1);
        assertThat(orders.get(id).status()).isEqualTo("READY");
        assertThat(jdbc.sql("select count(*) from ordering.processed_event where event_id = ?")
                        .param(eventId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    private void send(UUID eventId, String type, UUID orderId, String reason) {
        var json = "{\"eventId\":\"%s\",\"type\":\"%s\",\"orderId\":\"%s\",\"workOrderId\":\"FW-%s\",\"reason\":%s}"
                .formatted(eventId, type, orderId, UUID.randomUUID(), reason == null ? "null" : "\"" + reason + "\"");
        KafkaProbe.send(kafka.getBootstrapServers(), "forge.events", orderId.toString(), json);
    }
}

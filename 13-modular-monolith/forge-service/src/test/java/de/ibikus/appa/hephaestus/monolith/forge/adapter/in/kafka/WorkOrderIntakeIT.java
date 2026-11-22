package de.ibikus.appa.hephaestus.monolith.forge.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.monolith.forge.Containers;
import de.ibikus.appa.hephaestus.monolith.forge.FakeSupplier;
import de.ibikus.appa.hephaestus.monolith.forge.KafkaProbe;
import de.ibikus.appa.hephaestus.monolith.forge.application.WorkOrders;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/** The service's Kafka side, the test playing the monolith: requests in (once each), outcomes out (outbox). */
@SpringBootTest
@Import(Containers.class)
class WorkOrderIntakeIT {

    static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    KafkaContainer kafka;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    WorkOrders workOrders;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @Test
    void duplicateRequestCreatesOneWorkOrder() {
        var orderId = UUID.randomUUID();
        request(orderId, "IRON-01", 1);
        request(orderId, "IRON-01", 1);
        await().atMost(WAIT).until(() -> count(orderId) == 1);
        // the second delivery has had its chance too: still one
        var other = UUID.randomUUID();
        request(other, "IRON-01", 1);
        await().atMost(WAIT).until(() -> count(other) == 1);
        assertThat(count(orderId)).isEqualTo(1);
    }

    @Test
    void completionIsPublished() {
        var orderId = UUID.randomUUID();
        request(orderId, "IRON-01", 1);
        await().atMost(WAIT).until(() -> count(orderId) == 1);
        var id = jdbc.sql("select id from work_order where order_id = ?")
                .param(orderId)
                .query(String.class)
                .single();
        workOrders.start(id);
        workOrders.complete(id);

        var record = KafkaProbe.read(kafka.getBootstrapServers(), "forge.events", orderId.toString(), WAIT);
        assertThat(record).hasValueSatisfying(json -> assertThat(json)
                .contains("\"type\":\"WorkOrderCompleted\"")
                .contains("\"workOrderId\":\"" + id + "\"")
                .contains("\"eventId\":\""));
    }

    @Test
    void failureIsPublishedWithItsReason() {
        var orderId = UUID.randomUUID();
        request(orderId, "MITHRIL-01", 1);
        var record = KafkaProbe.read(kafka.getBootstrapServers(), "forge.events", orderId.toString(), WAIT);
        assertThat(record).hasValueSatisfying(json -> assertThat(json)
                .contains("\"type\":\"WorkOrderFailed\"")
                .contains("material not deliverable: MITHRIL-01"));
    }

    private void request(UUID orderId, String material, int quantity) {
        KafkaProbe.send(
                kafka.getBootstrapServers(),
                "forge.requests",
                orderId.toString(),
                "{\"orderId\":\"%s\",\"material\":\"%s\",\"quantity\":%d}".formatted(orderId, material, quantity));
    }

    private int count(UUID orderId) {
        return jdbc.sql("select count(*) from work_order where order_id = ?")
                .param(orderId)
                .query(Integer.class)
                .single();
    }
}

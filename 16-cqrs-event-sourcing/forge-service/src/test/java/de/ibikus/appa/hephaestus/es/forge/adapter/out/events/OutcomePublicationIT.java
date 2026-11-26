package de.ibikus.appa.hephaestus.es.forge.adapter.out.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.es.forge.AvroProbe;
import de.ibikus.appa.hephaestus.es.forge.Containers;
import de.ibikus.appa.hephaestus.es.forge.FakeSupplier;
import de.ibikus.appa.hephaestus.es.forge.application.WorkOrders;
import de.ibikus.appa.hephaestus.contracts.events.ForgeEvent;
import de.ibikus.appa.hephaestus.contracts.events.ForgeEventType;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/** es.forge.events: outcomes leave through the outbox relay as Avro, readable with the registered schema. */
@SpringBootTest
@Import(Containers.class)
class OutcomePublicationIT {

    static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    KafkaContainer kafka;

    @Autowired
    GenericContainer<?> apicurio;

    @Autowired
    WorkOrders workOrders;

    @Autowired
    JdbcClient jdbc;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @Test
    void completedWorkOrderEmitsAvroEvent() {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "IRON-01", 1, Optional.empty());
        var id = workOrderOf(orderId);

        workOrders.start(id);
        workOrders.complete(id);

        assertThat(read(orderId)).hasValueSatisfying(value -> {
            var event = (ForgeEvent) value;
            assertThat(event.getType()).isEqualTo(ForgeEventType.WORK_ORDER_COMPLETED);
            assertThat(event.getWorkOrderId()).isEqualTo(id);
            assertThat(event.getEventId()).isNotBlank();
        });
    }

    @Test
    void failureIsPublishedWithItsReason() {
        var orderId = UUID.randomUUID();

        workOrders.request(orderId, "MITHRIL-01", 1, Optional.empty());

        assertThat(read(orderId)).hasValueSatisfying(value -> {
            var event = (ForgeEvent) value;
            assertThat(event.getType()).isEqualTo(ForgeEventType.WORK_ORDER_FAILED);
            assertThat(event.getReason()).isEqualTo("material not deliverable: MITHRIL-01");
        });
    }

    private Optional<Object> read(UUID orderId) {
        return AvroProbe.read(
                kafka.getBootstrapServers(), Containers.url(apicurio), "es.forge.events", orderId.toString(), WAIT);
    }

    private String workOrderOf(UUID orderId) {
        await().atMost(WAIT)
                .until(() -> jdbc.sql("select count(*) from work_order where order_id = ?")
                                .param(orderId)
                                .query(Integer.class)
                                .single()
                        == 1);
        return jdbc.sql("select id from work_order where order_id = ?")
                .param(orderId)
                .query(String.class)
                .single();
    }
}

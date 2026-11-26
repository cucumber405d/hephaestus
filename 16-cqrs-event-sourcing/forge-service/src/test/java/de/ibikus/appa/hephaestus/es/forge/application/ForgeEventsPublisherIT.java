package de.ibikus.appa.hephaestus.es.forge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.contracts.events.ForgeEvent;
import de.ibikus.appa.hephaestus.contracts.events.ForgeEventType;
import de.ibikus.appa.hephaestus.es.forge.AvroProbe;
import de.ibikus.appa.hephaestus.es.forge.Containers;
import de.ibikus.appa.hephaestus.es.forge.FakeSupplier;
import de.ibikus.appa.hephaestus.es.forge.Journal;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventCodec;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventStore;
import de.ibikus.appa.hephaestus.es.forge.application.projection.CheckpointStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/** The journal as outbox: outcomes reach es.forge.events once, as Avro, behind their own checkpoint. */
@SpringBootTest
@Import({Containers.class, Journal.class})
class ForgeEventsPublisherIT {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final Duration QUIET = Duration.ofSeconds(4);

    @Autowired
    KafkaContainer kafka;

    @Autowired
    GenericContainer<?> apicurio;

    @Autowired
    WorkOrders workOrders;

    @Autowired
    Journal journal;

    @Autowired
    EventStore store;

    @Autowired
    EventCodec codec;

    @Autowired
    CheckpointStore checkpoints;

    @Autowired
    WorkOrderRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @Test
    void completedAndFailedArePublishedOnce() {
        var done = completedOrder();
        var failed = UUID.randomUUID();
        workOrders.request(failed, "MITHRIL-01", 1, Optional.empty());

        var completedEvents = readAll(done);
        assertThat(completedEvents).hasSize(1);
        var completed = (ForgeEvent) completedEvents.getFirst();
        assertThat(completed.getType()).isEqualTo(ForgeEventType.WORK_ORDER_COMPLETED);
        assertThat(completed.getWorkOrderId()).isEqualTo("FW-" + done);
        assertThat(completed.getEventId()).isNotBlank();

        var failedEvents = readAll(failed);
        assertThat(failedEvents).hasSize(1);
        var failure = (ForgeEvent) failedEvents.getFirst();
        assertThat(failure.getType()).isEqualTo(ForgeEventType.WORK_ORDER_FAILED);
        assertThat(failure.getReason()).isEqualTo("material not deliverable: MITHRIL-01");
    }

    @Test
    void materialAndStartedEventsAreNotPublished() {
        var started = UUID.randomUUID();
        workOrders.request(started, "IRON-01", 1, Optional.empty());
        workOrders.start("FW-" + started, "brokk");
        var later = completedOrder();
        assertThat(read(later)).isPresent();

        assertThat(AvroProbe.readAll(bootstrap(), registry(), "es.forge.events", started.toString(), QUIET))
                .isEmpty();
    }

    @Test
    void historicalFixtureIsNotPublished() {
        var later = completedOrder();
        assertThat(read(later)).isPresent();

        assertThat(AvroProbe.readAll(
                        bootstrap(), registry(), "es.forge.events", "00000000-0000-0000-0000-000000000001", QUIET))
                .isEmpty();
    }

    @Test
    void kafkaDownKeepsCheckpointAndRepublishesLater() {
        var name = "publisher-test-" + UUID.randomUUID();
        // start right behind the journal's head: the only outcome after it is this test's own
        jdbc.sql("""
                        insert into read.projection_checkpoint(name, tx_id, position)
                        select ?, tx_id, global_position from forge.event
                        order by tx_id desc, global_position desc limit 1""").param(name).update();
        var start = checkpoints.get(name);
        var orderId = completedOrder();
        var sink = new SwitchableSink();
        var publisher = new OutcomePublisher(store, codec, checkpoints, repository, sink, tx, name, 500);

        sink.down = true;
        assertThatThrownBy(publisher::publishOnce).isInstanceOf(IllegalStateException.class);
        assertThat(checkpoints.get(name)).isEqualTo(start);
        assertThat(sink.attempts).extracting(Outcome::orderId).containsExactly(orderId);
        var attempted = sink.attempts.getFirst();

        sink.down = false;
        while (publisher.publishOnce() > 0) {
            // drain the whole journal
        }
        var delivered =
                sink.delivered.stream().filter(o -> o.orderId().equals(orderId)).toList();
        assertThat(delivered).hasSize(1);
        assertThat(delivered.getFirst().eventId()).isEqualTo(attempted.eventId());
        assertThat(checkpoints.get(name).position()).isGreaterThan(start.position());
    }

    private UUID completedOrder() {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "IRON-01", 1, Optional.empty());
        await().atMost(WAIT).until(() -> "ALLOCATED".equals(journal.status(orderId)));
        workOrders.start("FW-" + orderId, "brokk");
        workOrders.complete("FW-" + orderId);
        return orderId;
    }

    private Optional<Object> read(UUID orderId) {
        return AvroProbe.read(bootstrap(), registry(), "es.forge.events", orderId.toString(), WAIT);
    }

    private List<Object> readAll(UUID orderId) {
        await().atMost(WAIT).until(() -> read(orderId).isPresent());
        return AvroProbe.readAll(bootstrap(), registry(), "es.forge.events", orderId.toString(), QUIET);
    }

    private String bootstrap() {
        return kafka.getBootstrapServers();
    }

    private String registry() {
        return Containers.url(apicurio);
    }

    /** A sink that refuses every outcome while down (the broker is unreachable) and records what it got. */
    static final class SwitchableSink implements OutcomeSink {
        volatile boolean down;
        final List<Outcome> attempts = new ArrayList<>();
        final List<Outcome> delivered = new ArrayList<>();

        @Override
        public void send(Outcome outcome) {
            attempts.add(outcome);
            if (down) {
                throw new IllegalStateException("broker unreachable");
            }
            delivered.add(outcome);
        }
    }
}

package de.ibikus.appa.hephaestus.es.forge.adapter.out.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.Checkpoint;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.ConcurrencyConflict;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.NewEvent;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.RecordedEvent;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * The journal on a real PostgreSQL, without Spring: appends, stream concurrency, immutability and the global read
 * that must never skip an event of a transaction still open (the order is (tx_id, position), below the snapshot's
 * xmin). Open transactions are plain connections with auto-commit off, so the interleaving is deterministic.
 */
class EventStoreIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    static final JsonMapper JSON = JsonMapper.builder().build();
    static DataSource dataSource;
    static JdbcEventStore store;

    @BeforeAll
    static void start() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema("public")
                .load()
                .migrate();
        store = new JdbcEventStore(JdbcClient.create(dataSource), JSON);
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    void appendedEventsReadBackInStreamOrder() {
        var stream = stream();
        store.append(stream, "Test", 0, List.of(event("A", Map.of("n", 1)), event("B", Map.of("n", 2))));
        store.append(stream, "Test", 2, List.of(event("C", Map.of("n", 3))));

        var events = store.readStream(stream);

        assertThat(events).extracting(RecordedEvent::version).containsExactly(1, 2, 3);
        assertThat(events).extracting(RecordedEvent::type).containsExactly("A", "B", "C");
        assertThat(events.get(2).payload().get("n").asInt()).isEqualTo(3);
        assertThat(events.get(0).metadata()).containsEntry("correlationId", "c-1");
        assertThat(events.get(0).streamType()).isEqualTo("Test");
    }

    @Test
    void secondWriterWithSameExpectedVersionGetsConflict() {
        var stream = stream();
        store.append(stream, "Test", 0, List.of(event("A", Map.of())));
        store.append(stream, "Test", 1, List.of(event("B", Map.of())));

        assertThatThrownBy(() -> store.append(stream, "Test", 1, List.of(event("B2", Map.of()))))
                .isInstanceOf(ConcurrencyConflict.class);
        assertThatThrownBy(() -> store.append(stream, "Test", 5, List.of(event("Gap", Map.of()))))
                .isInstanceOf(ConcurrencyConflict.class);
        assertThat(store.readStream(stream)).extracting(RecordedEvent::type).containsExactly("A", "B");
    }

    @Test
    void journalRowsCannotBeUpdatedOrDeleted() {
        var stream = stream();
        store.append(stream, "Test", 0, List.of(event("A", Map.of())));
        var jdbc = JdbcClient.create(dataSource);

        assertThatThrownBy(() -> jdbc.sql("update forge.event set type = 'X' where stream_id = ?")
                        .param(stream)
                        .update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from forge.event where stream_id = ?")
                        .param(stream)
                        .update())
                .hasMessageContaining("append-only");
    }

    @Test
    void readAllWaitsForOpenTransactions() throws Exception {
        var head = head();
        try (var a = open()) {
            on(a).append(stream(), "Test", 0, List.of(event("Open", Map.of()))); // A: lower xid, still open
            store.append(stream(), "Test", 0, List.of(event("Committed", Map.of()))); // committed, higher xid

            // Nothing past the oldest open transaction is visible: the committed event waits, it is not lost later
            assertThat(store.readAll(head, 100)).isEmpty();

            a.commit();
            assertThat(store.readAll(head, 100)).extracting(RecordedEvent::type).containsExactly("Open", "Committed");
        }
    }

    @Test
    void readAllLosesNothingBetweenPositionsOfOneTransaction() throws Exception {
        var head = head();
        try (var b = open();
                var a = open()) {
            var storeB = on(b);
            var storeA = on(a);
            storeB.append(stream(), "Test", 0, List.of(event("B1", Map.of()))); // B gets the lower xid, position p1
            storeA.append(stream(), "Test", 0, List.of(event("A1", Map.of()))); // A: higher xid, position p2
            storeB.append(stream(), "Test", 0, List.of(event("B2", Map.of()))); // B again: position p3 > p2
            b.commit();

            var first = store.readAll(head, 100);
            assertThat(first).extracting(RecordedEvent::type).containsExactly("B1", "B2");

            a.commit();
            var after = new Checkpoint(first.getLast().txId(), first.getLast().position());
            assertThat(store.readAll(after, 100))
                    .extracting(RecordedEvent::type)
                    .containsExactly("A1");
        }
    }

    /** Everything committed so far: the tests share the journal. */
    private static Checkpoint head() {
        var all = store.readAll(Checkpoint.START, Integer.MAX_VALUE);
        return all.isEmpty()
                ? Checkpoint.START
                : new Checkpoint(all.getLast().txId(), all.getLast().position());
    }

    private static Connection open() throws Exception {
        var connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    private static JdbcEventStore on(Connection connection) {
        return new JdbcEventStore(JdbcClient.create(new SingleConnectionDataSource(connection, true)), JSON);
    }

    private static String stream() {
        return "test-" + UUID.randomUUID();
    }

    private static NewEvent event(String type, Map<String, Object> payload) {
        return new NewEvent(type, 1, payload, Map.of("correlationId", "c-1"));
    }
}

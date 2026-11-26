package de.ibikus.appa.hephaestus.es.forge.adapter.out.projection;

import de.ibikus.appa.hephaestus.es.forge.adapter.out.eventstore.JdbcEventStore;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventCodec;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.WorkOrderStartedV1ToV2;
import de.ibikus.appa.hephaestus.es.forge.application.projection.Projection;
import de.ibikus.appa.hephaestus.es.forge.application.projection.ProjectionAdmin;
import de.ibikus.appa.hephaestus.es.forge.application.projection.ProjectionRunner;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/** A migrated PostgreSQL of its own and the projection machinery wired by hand (no Spring context, no Kafka). */
final class ProjectionHarness implements AutoCloseable {

    final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    final DataSource dataSource;
    final JdbcClient jdbc;
    final TransactionTemplate tx;
    final JdbcEventStore store;
    final EventCodec codec;
    final JdbcCheckpointStore checkpoints;
    final WorkshopBoardProjection board;
    final SmithLoadProjection smithLoad;
    final StockLedgerProjection ledger;

    ProjectionHarness() {
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema("public")
                .load()
                .migrate();
        jdbc = JdbcClient.create(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var json = JsonMapper.builder().build();
        store = new JdbcEventStore(jdbc, json);
        codec = new EventCodec(json, List.of(new WorkOrderStartedV1ToV2()));
        checkpoints = new JdbcCheckpointStore(jdbc);
        board = new WorkshopBoardProjection(jdbc);
        smithLoad = new SmithLoadProjection(jdbc);
        ledger = new StockLedgerProjection(jdbc);
    }

    ProjectionRunner runner(List<Projection> projections, Predicate<String> enabled, int batchSize) {
        return new ProjectionRunner(projections, store, codec, checkpoints, tx, enabled, batchSize);
    }

    ProjectionAdmin admin(List<Projection> projections, ProjectionRunner runner) {
        return new ProjectionAdmin(projections, checkpoints, tx, runner);
    }

    void append(String streamId, String streamType, int expectedVersion, Object... events) {
        tx.executeWithoutResult(status -> store.append(
                streamId,
                streamType,
                expectedVersion,
                java.util.Arrays.stream(events).map(e -> codec.encode(e, Map.of())).toList()));
    }

    @Override
    public void close() {
        postgres.stop();
    }
}

package de.ibikus.appa.hephaestus.es.forge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.es.forge.Containers;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** V5's history: WorkOrderStarted written in schema version 1 (no smith) reads as started by "unknown". */
@SpringBootTest
@Import(Containers.class)
class HistoricalFixtureIT {

    @Autowired
    WorkshopBoard board;

    @Autowired
    JdbcClient jdbc;

    @Test
    void historicalStartedV1ShowsUnknownSmithOnBoard() {
        assertThat(jdbc.sql("select count(*) from forge.event where type = 'WorkOrderStarted' and schema_version = 1")
                        .query(Integer.class)
                        .single())
                .isEqualTo(3);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(board.list(Optional.of("DONE")))
                        .filteredOn(e -> e.id().startsWith("FW-HIST-"))
                        .extracting(BoardEntry::smith)
                        .containsExactly("unknown", "unknown", "unknown"));
    }
}

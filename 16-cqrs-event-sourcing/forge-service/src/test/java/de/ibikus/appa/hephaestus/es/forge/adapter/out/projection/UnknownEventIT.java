package de.ibikus.appa.hephaestus.es.forge.adapter.out.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A journal row this build cannot read stops the projection before it — on a database of its own (rows are final). */
class UnknownEventIT {

    static ProjectionHarness h;

    @BeforeAll
    static void start() {
        h = new ProjectionHarness();
    }

    @AfterAll
    static void stop() {
        h.close();
    }

    @Test
    void unknownEventStopsProjection() {
        var runner = h.runner(List.of(h.board), name -> true, 500);
        while (runner.runOnce() > 0) {}
        var before = position();
        h.jdbc.sql("""
                        insert into forge.event (stream_id, stream_type, version, type, schema_version, payload, metadata)
                        values ('bogus-1', 'WorkOrder', 1, 'Bogus', 1, '{}'::jsonb, '{}'::jsonb)""")
                .update();

        runner.runOnce();
        runner.runOnce();

        assertThat(position()).isEqualTo(before);
        assertThat(runner.lastError(h.board.name())).contains("Bogus");
    }

    private static long position() {
        return h.jdbc.sql("select position from read.projection_checkpoint where name = 'workshop-board'")
                .query(Long.class)
                .single();
    }
}

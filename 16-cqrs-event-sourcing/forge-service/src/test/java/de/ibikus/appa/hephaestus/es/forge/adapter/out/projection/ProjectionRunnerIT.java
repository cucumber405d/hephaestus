package de.ibikus.appa.hephaestus.es.forge.adapter.out.projection;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.RecordedEvent;
import de.ibikus.appa.hephaestus.es.forge.application.projection.Projection;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialDecider;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived.Source;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockTaken;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderDecider;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.MaterialAllocated;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderRequested;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderStarted;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProjectionRunnerIT {

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
    void boardShowsLatestStatusAndSmith() {
        var id = workOrder("anna", true);
        catchUp(List.of(h.board));

        var row = h.jdbc.sql("select status, smith from read.workshop_board where work_order_id = ?")
                .param(id)
                .query((rs, n) -> rs.getString(1) + "/" + rs.getString(2))
                .single();
        assertThat(row).isEqualTo("DONE/anna");
    }

    @Test
    void smithLoadCountsInProgressAndDone() {
        workOrder("boris", true);
        workOrder("boris", false);
        catchUp(List.of(h.smithLoad));

        var load = h.jdbc.sql("select in_progress, done from read.smith_load where smith = 'boris'")
                .query((rs, n) -> rs.getInt(1) + "/" + rs.getInt(2))
                .single();
        assertThat(load).isEqualTo("1/1");
    }

    @Test
    void stockLedgerSumsMovements() {
        var code = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        var stream = MaterialDecider.streamId(code);
        h.append(stream, "Material", 0, new StockReceived(20, Source.INITIAL), new StockTaken(5, "FW-x"));
        h.append(stream, "Material", 2, new StockReceived(3, Source.SUPPLIER));
        catchUp(List.of(h.ledger));

        assertThat(onHand(code)).isEqualTo(18);
        assertThat(h.jdbc.sql("select count(*) from read.stock_movement where material = ?")
                        .param(code)
                        .query(Integer.class)
                        .single())
                .isEqualTo(3);
    }

    @Test
    void failingHandlerRollsBackBatchAndRetries() {
        h.jdbc.sql("create table if not exists read.flaky_seen (position bigint primary key)")
                .update();
        h.jdbc.sql("insert into read.projection_checkpoint (name, tx_id, position) values ('flaky', '0', 0)"
                        + " on conflict do nothing")
                .update();
        workOrder("clara", true);
        var flaky = new Flaky(h);
        var runner = h.runner(List.of(flaky), name -> true, 500);

        runner.runOnce(); // the 2nd event of the batch throws: nothing of the batch may stay
        assertThat(count("select count(*) from read.flaky_seen")).isZero();
        assertThat(count("select position from read.projection_checkpoint where name = 'flaky'"))
                .isZero();

        while (runner.runOnce() > 0) {}
        assertThat(count("select count(*) from read.flaky_seen")).isEqualTo(count("select count(*) from forge.event"));
    }

    @Test
    void rebuildDuringCatchUpEqualsCleanRebuild() throws Exception {
        for (int i = 0; i < 100; i++) {
            workOrder("smith-" + (i % 5), i % 2 == 0);
        }
        var projections = List.<Projection>of(h.board);
        var runner = h.runner(projections, name -> true, 20);
        var admin = h.admin(projections, runner);
        var catchingUp = new Thread(() -> {
            while (runner.runOnce() > 0) {}
        });
        catchingUp.start();
        Thread.sleep(50);
        admin.rebuild(h.board.name());
        catchingUp.join();
        while (runner.runOnce() > 0) {}
        var afterRace = boardSnapshot();

        admin.rebuild(h.board.name());
        while (runner.runOnce() > 0) {}

        assertThat(afterRace).isEqualTo(boardSnapshot());
    }

    @Test
    void disabledProjectionCatchesUpWhenEnabled() {
        var enabled = new AtomicBoolean(false);
        var runner = h.runner(List.of(h.smithLoad), name -> enabled.get(), 500);
        workOrder("dora", true);
        workOrder("dora", true);

        while (runner.runOnce() > 0) {}
        assertThat(h.jdbc.sql("select count(*) from read.smith_load where smith = 'dora'")
                        .query(Integer.class)
                        .single())
                .isZero();

        enabled.set(true);
        while (runner.runOnce() > 0) {}
        assertThat(h.jdbc.sql("select done from read.smith_load where smith = 'dora'")
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
    }

    private static void catchUp(List<Projection> projections) {
        var runner = h.runner(projections, name -> true, 500);
        while (runner.runOnce() > 0) {}
    }

    private static List<String> boardSnapshot() {
        return h.jdbc.sql("select work_order_id || status || coalesce(smith, '') from read.workshop_board order by 1")
                .query(String.class)
                .list();
    }

    private static int onHand(String code) {
        return h.jdbc.sql("select on_hand from read.stock_ledger where material = ?")
                .param(code)
                .query(Integer.class)
                .single();
    }

    /** A work order through start (and completion when {@code complete}), straight into the journal. */
    private static String workOrder(String smith, boolean complete) {
        var order = UUID.randomUUID();
        var id = WorkOrderDecider.idFor(order);
        var events = new ArrayList<Object>(List.of(
                new WorkOrderRequested(id, order, "IRON-01", 1),
                new MaterialAllocated(1),
                new WorkOrderStarted(smith)));
        if (complete) {
            events.add(new WorkOrderCompleted());
        }
        h.append(id, "WorkOrder", 0, events.toArray());
        return id;
    }

    private static long count(String sql) {
        return h.jdbc.sql(sql).query(Long.class).single();
    }

    /** Records every position it sees; fails once, on the second event of its first batch. */
    static final class Flaky implements Projection {

        final ProjectionHarness h;
        int seen;
        boolean failed;

        Flaky(ProjectionHarness h) {
            this.h = h;
        }

        @Override
        public String name() {
            return "flaky";
        }

        @Override
        public void apply(Object event, RecordedEvent meta) {
            if (!failed && ++seen == 2) {
                failed = true;
                throw new IllegalStateException("handler failure (test)");
            }
            h.jdbc.sql("insert into read.flaky_seen (position) values (?)")
                    .param(meta.position())
                    .update();
        }

        @Override
        public void reset() {
            h.jdbc.sql("delete from read.flaky_seen").update();
        }
    }
}

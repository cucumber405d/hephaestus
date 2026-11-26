package de.ibikus.appa.hephaestus.es.forge.adapter.out.projection;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.RecordedEvent;
import de.ibikus.appa.hephaestus.es.forge.application.projection.Projection;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderStarted;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * smith-load: work in progress and done per smith. Keeps its own work order → smith table (it may not read another
 * projection: each has its own checkpoint, so the other may lag or be rebuilding).
 */
@Component
public class SmithLoadProjection implements Projection {

    private final JdbcClient jdbc;

    public SmithLoadProjection(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "smith-load";
    }

    @Override
    public void apply(Object event, RecordedEvent meta) {
        switch (event) {
            case WorkOrderStarted s -> {
                jdbc.sql("insert into read.smith_assignment (work_order_id, smith) values (?, ?)")
                        .params(meta.streamId(), s.smith())
                        .update();
                jdbc.sql("""
                                insert into read.smith_load (smith, in_progress, done) values (?, 1, 0)
                                on conflict (smith) do update set in_progress = read.smith_load.in_progress + 1""")
                        .param(s.smith())
                        .update();
            }
            case WorkOrderCompleted ignored ->
                jdbc.sql("""
                                update read.smith_load
                                   set in_progress = in_progress - 1, done = done + 1
                                 where smith = (select smith from read.smith_assignment where work_order_id = ?)""")
                        .param(meta.streamId())
                        .update();
            default -> {}
        }
    }

    @Override
    public void reset() {
        jdbc.sql("delete from read.smith_load").update();
        jdbc.sql("delete from read.smith_assignment").update();
    }
}

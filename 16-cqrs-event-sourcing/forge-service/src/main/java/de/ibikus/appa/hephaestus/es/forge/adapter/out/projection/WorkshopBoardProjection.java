package de.ibikus.appa.hephaestus.es.forge.adapter.out.projection;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.RecordedEvent;
import de.ibikus.appa.hephaestus.es.forge.application.projection.Projection;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.MaterialAllocated;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderFailed;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderRequested;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderStarted;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** workshop-board: every work order with its latest status (read.workshop_board). */
@Component
public class WorkshopBoardProjection implements Projection {

    private final JdbcClient jdbc;

    public WorkshopBoardProjection(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "workshop-board";
    }

    @Override
    public void apply(Object event, RecordedEvent meta) {
        if (!(event instanceof WorkOrderEvent workOrderEvent)) {
            return;
        }
        var at = Timestamp.from(meta.recordedAt());
        switch (workOrderEvent) {
            case WorkOrderRequested r ->
                jdbc.sql("""
                                insert into read.workshop_board (work_order_id, order_id, material, quantity, status,
                                    last_event_recorded_at, updated_at)
                                values (?, ?, ?, ?, 'WAITING_FOR_MATERIAL', ?, clock_timestamp())""")
                        .params(r.workOrderId(), r.orderId(), r.material(), r.quantity(), at)
                        .update();
            case WorkOrderFailed f -> update(meta.streamId(), "FAILED", null, f.reason(), at);
            case MaterialAllocated ignored -> update(meta.streamId(), "ALLOCATED", null, null, at);
            case WorkOrderStarted s -> update(meta.streamId(), "IN_PROGRESS", s.smith(), null, at);
            case WorkOrderCompleted ignored -> update(meta.streamId(), "DONE", null, null, at);
        }
    }

    @Override
    public void reset() {
        jdbc.sql("delete from read.workshop_board").update();
    }

    /** {@code smith}/{@code reason} null = keep the current value. */
    private void update(String workOrderId, String status, String smith, String reason, Timestamp at) {
        jdbc.sql("""
                        update read.workshop_board
                           set status = ?, smith = coalesce(?, smith), failure_reason = coalesce(?, failure_reason),
                               last_event_recorded_at = ?, updated_at = clock_timestamp()
                         where work_order_id = ?""")
                .params(status, smith, reason, at, workOrderId)
                .update();
    }
}

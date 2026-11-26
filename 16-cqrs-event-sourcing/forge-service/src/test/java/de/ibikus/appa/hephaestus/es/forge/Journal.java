package de.ibikus.appa.hephaestus.es.forge;

import de.ibikus.appa.hephaestus.es.forge.application.WorkOrderRepository;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderDecider;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderState;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Tests read the source of truth: the work order as its stream says, and raw counts in forge.event. */
@TestComponent
public class Journal {

    private final WorkOrderRepository workOrders;
    private final JdbcClient jdbc;

    public Journal(WorkOrderRepository workOrders, JdbcClient jdbc) {
        this.workOrders = workOrders;
        this.jdbc = jdbc;
    }

    public WorkOrderState workOrder(UUID orderId) {
        return workOrders.load(WorkOrderDecider.idFor(orderId)).state();
    }

    public String status(UUID orderId) {
        return workOrder(orderId).status().name();
    }

    public int events(String streamId) {
        return jdbc.sql("select count(*) from forge.event where stream_id = ?")
                .param(streamId)
                .query(Integer.class)
                .single();
    }

    public int events(String streamId, String type) {
        return jdbc.sql("select count(*) from forge.event where stream_id = ? and type = ?")
                .params(streamId, type)
                .query(Integer.class)
                .single();
    }

    public int requests(UUID orderId) {
        return jdbc.sql(
                        "select count(*) from forge.event where type = 'WorkOrderRequested' and payload->>'orderId' = ?")
                .param(orderId.toString())
                .query(Integer.class)
                .single();
    }
}

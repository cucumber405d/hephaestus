package de.ibikus.appa.hephaestus.monolith.modular.forge.internal;

import de.ibikus.appa.hephaestus.monolith.modular.forge.ForgeStillBusy;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrder;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderFailed;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrders;
import de.ibikus.appa.hephaestus.monolith.modular.supplier.SupplierGateway;
import de.ibikus.appa.hephaestus.monolith.modular.supplier.SupplyResult.Delivered;
import de.ibikus.appa.hephaestus.monolith.modular.supplier.SupplyResult.PermanentFailure;
import de.ibikus.appa.hephaestus.monolith.modular.supplier.SupplyResult.TransientFailure;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Work orders in forge's own schema. Material comes from forge's stock, the shortfall from the supplier through the
 * ACL; the outcome leaves as an event. Forge knows nothing about customer orders' table or inventory.
 */
@Service
class Forge implements WorkOrders {

    private static final String COLUMNS = "id, order_id, material, quantity, status, 'MONOLITH' as origin";

    private final JdbcClient jdbc;
    private final SupplierGateway supplier;
    private final ApplicationEventPublisher events;

    Forge(JdbcClient jdbc, SupplierGateway supplier, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.supplier = supplier;
        this.events = events;
    }

    @Override
    @Transactional
    public void request(UUID orderId, String material, int quantity) {
        var id = "WO-%06d"
                .formatted(jdbc.sql("select nextval('forge.work_order_seq')")
                        .query(Long.class)
                        .single());
        var created = jdbc.sql("""
                        insert into forge.work_order
                            (id, order_id, material, quantity, status, material_ready, created_at)
                        values (?, ?, ?, ?, 'PLANNED', false, clock_timestamp())
                        on conflict (order_id) do nothing""").params(id, orderId, material, quantity).update();
        if (created == 1) {
            supply(id);
        }
    }

    /** Takes the material from stock, orders the shortfall; an unreachable supplier leaves it for the retry. */
    @Transactional
    void supply(String id) {
        var wo = jdbc.sql("select " + COLUMNS + " from forge.work_order where id = ? and status = 'PLANNED'"
                        + " and not material_ready for update")
                .param(id)
                .query(WorkOrder.class)
                .optional()
                .orElse(null);
        if (wo == null) {
            return;
        }
        var onHand = jdbc.sql("select on_hand from forge.material where code = ? for update")
                .param(wo.material())
                .query(Integer.class)
                .optional()
                .orElse(0);
        if (onHand < wo.quantity()) {
            switch (supplier.order(wo.material(), wo.quantity() - onHand)) {
                case Delivered delivered ->
                    jdbc.sql("""
                                insert into forge.material (code, on_hand) values (?, ?)
                                on conflict (code) do update set on_hand = forge.material.on_hand + excluded.on_hand""").params(wo.material(), delivered.quantity()).update();
                case PermanentFailure failure -> {
                    fail(wo, failure);
                    return;
                }
                case TransientFailure ignored -> {
                    return;
                }
            }
        }
        jdbc.sql("update forge.material set on_hand = on_hand - ? where code = ?")
                .params(wo.quantity(), wo.material())
                .update();
        jdbc.sql("update forge.work_order set material_ready = true where id = ?")
                .param(id)
                .update();
    }

    private void fail(WorkOrder wo, PermanentFailure failure) {
        var reason = switch (failure.reason()) {
            case PermanentFailure.NOT_DELIVERABLE -> "material not deliverable: " + wo.material();
            default -> "unknown material: " + wo.material();
        };
        jdbc.sql("update forge.work_order set status = 'FAILED', failure_reason = ? where id = ?")
                .params(reason, wo.id())
                .update();
        events.publishEvent(new WorkOrderFailed(wo.orderId(), wo.id(), reason));
    }

    List<String> waitingForMaterial() {
        return jdbc.sql("select id from forge.work_order where status = 'PLANNED' and not material_ready")
                .query(String.class)
                .list();
    }

    @Override
    public List<WorkOrder> list(String status) {
        return jdbc.sql("select " + COLUMNS + " from forge.work_order"
                        + " where (cast(? as varchar) is null or status = ?) order by created_at")
                .params(status, status)
                .query(WorkOrder.class)
                .list();
    }

    @Override
    @Transactional
    public void start(String id) {
        if (jdbc.sql("update forge.work_order set status = 'IN_PROGRESS'"
                                + " where id = ? and status = 'PLANNED' and material_ready")
                        .param(id)
                        .update()
                == 0) {
            throw refusal(id, "cannot start");
        }
    }

    @Override
    @Transactional
    public void complete(String id) {
        if (jdbc.sql("update forge.work_order set status = 'DONE' where id = ? and status = 'IN_PROGRESS'")
                        .param(id)
                        .update()
                == 0) {
            throw refusal(id, "cannot complete");
        }
        var orderId = jdbc.sql("select order_id from forge.work_order where id = ?")
                .param(id)
                .query(UUID.class)
                .single();
        events.publishEvent(new WorkOrderCompleted(orderId, id));
    }

    @Override
    public List<WorkOrder> open() {
        return jdbc.sql("select " + COLUMNS + " from forge.work_order"
                        + " where status in ('PLANNED', 'IN_PROGRESS') order by created_at")
                .query(WorkOrder.class)
                .list();
    }

    @Override
    public boolean decommissioned() {
        return jdbc.sql("select decommissioned from forge.lifecycle where id = 1")
                .query(Boolean.class)
                .single();
    }

    @Override
    @Transactional
    public void decommission() {
        // Lock the lifecycle row: no work order can slip in between the count and the switch
        jdbc.sql("select decommissioned from forge.lifecycle where id = 1 for update")
                .query(Boolean.class)
                .single();
        var open = jdbc.sql("select count(*) from forge.work_order where status in ('PLANNED', 'IN_PROGRESS')")
                .query(Integer.class)
                .single();
        if (open > 0) {
            throw new ForgeStillBusy(open);
        }
        jdbc.sql("update forge.lifecycle set decommissioned = true where id = 1").update();
    }

    private RuntimeException refusal(String id, String what) {
        var exists = jdbc.sql("select count(*) from forge.work_order where id = ?")
                        .param(id)
                        .query(Integer.class)
                        .single()
                > 0;
        return exists ? new WorkOrderConflict(id + ": " + what) : new WorkOrderNotFound(id);
    }
}

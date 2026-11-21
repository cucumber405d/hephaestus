package de.ibikus.appa.hephaestus.monolith.legacy.forge;

import de.ibikus.appa.hephaestus.monolith.legacy.ordering.OrderService;
import de.ibikus.appa.hephaestus.monolith.legacy.supplier.Antwort;
import de.ibikus.appa.hephaestus.monolith.legacy.supplier.Bestellung;
import de.ibikus.appa.hephaestus.monolith.legacy.supplier.SupplierClient;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The forge as it grew. Tangle 1: calls back into OrderService (cycle). Tangle 2: books finished pieces straight
 * into inventory's stock_item. Tangle 3: the supplier's Antwort and its codes drive the logic and are stored.
 */
@Service
public class ForgeService {

    private final JdbcClient jdbc;
    private final SupplierClient supplier;
    private final OrderService orders;

    ForgeService(JdbcClient jdbc, SupplierClient supplier, OrderService orders) {
        this.jdbc = jdbc;
        this.supplier = supplier;
        this.orders = orders;
    }

    public void createWorkOrder(UUID orderId, String material, int quantity) {
        var id = "WO-%06d"
                .formatted(jdbc.sql("select nextval('work_order_seq')")
                        .query(Long.class)
                        .single());
        var onHand = jdbc.sql("select on_hand from material where code = ?")
                .param(material)
                .query(Integer.class)
                .optional()
                .orElse(0);
        Integer lieferstatus = null;
        var status = "PLANNED";
        if (onHand < quantity) {
            Antwort antwort = supplier.bestellen(new Bestellung(material, quantity - onHand));
            lieferstatus = antwort.status();
            if (antwort.status() == Antwort.GELIEFERT) {
                jdbc.sql("update material set on_hand = on_hand + ? where code = ?")
                        .params(antwort.liefermenge(), material)
                        .update();
            } else {
                status = "FAILED";
                orders.reject(
                        orderId,
                        (antwort.status() == Antwort.NICHT_LIEFERBAR
                                        ? "material not deliverable: "
                                        : "unknown material: ")
                                + material);
            }
        }
        if (status.equals("PLANNED")) {
            jdbc.sql("update material set on_hand = on_hand - ? where code = ?")
                    .params(quantity, material)
                    .update();
        }
        jdbc.sql("""
                        insert into work_order (id, order_id, material, quantity, status, lieferstatus, created_at)
                        values (?, ?, ?, ?, ?, ?, clock_timestamp())""")
                .params(id, orderId, material, quantity, status, lieferstatus)
                .update();
    }

    public List<WorkOrder> list(String status) {
        return jdbc.sql("""
                        select id, order_id, material, quantity, status, lieferstatus, 'MONOLITH' as origin
                        from work_order where (cast(? as varchar) is null or status = ?) order by created_at""")
                .params(status, status)
                .query(WorkOrder.class)
                .list();
    }

    @Transactional
    public void start(String id) {
        transition(id, "PLANNED", "IN_PROGRESS");
    }

    @Transactional
    public void complete(String id) {
        transition(id, "IN_PROGRESS", "DONE");
        var wo = jdbc.sql("select id, order_id, material, quantity, status, lieferstatus, 'MONOLITH' as origin"
                        + " from work_order where id = ?")
                .param(id)
                .query(WorkOrder.class)
                .single();
        // Tangle 2: inventory's table, written by the forge
        jdbc.sql("""
                        insert into stock_item (sku, on_hand, reserved) values (?, ?, ?)
                        on conflict (sku) do update
                        set on_hand = stock_item.on_hand + excluded.on_hand,
                            reserved = stock_item.reserved + excluded.reserved""")
                .params("CUSTOM-" + wo.material(), wo.quantity(), wo.quantity())
                .update();
        orders.markReady(wo.orderId());
    }

    private void transition(String id, String from, String to) {
        var changed = jdbc.sql("update work_order set status = ? where id = ? and status = ?")
                .params(to, id, from)
                .update();
        if (changed == 0) {
            var exists = jdbc.sql("select count(*) from work_order where id = ?")
                            .param(id)
                            .query(Integer.class)
                            .single()
                    > 0;
            throw new ResponseStatusException(
                    exists ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND, id + " is not " + from);
        }
    }
}

package de.ibikus.appa.hephaestus.monolith.legacy.ordering;

import de.ibikus.appa.hephaestus.monolith.legacy.forge.ForgeService;
import de.ibikus.appa.hephaestus.monolith.legacy.inventory.Inventory;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tangle 1 (one side): Ordering calls the concrete ForgeService, and ForgeService calls back into this class —
 * a cycle. Tangle 4: one transaction spans the order, the work order and the supplier's HTTP call.
 */
@Service
public class OrderService {

    private final JdbcClient jdbc;
    private final Inventory inventory;
    private final ForgeService forge;

    // @Lazy: the cycle needs it to start at all — the first smell anyone notices
    OrderService(JdbcClient jdbc, Inventory inventory, @Lazy ForgeService forge) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.forge = forge;
    }

    @Transactional
    public UUID place(OrderRequest request) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                        insert into customer_order (id, customer_id, sku, custom, material, quantity, status, created_at)
                        values (?, ?, ?, ?, ?, ?, 'ACCEPTED', clock_timestamp())""")
                .params(
                        id,
                        request.customerId(),
                        request.sku(),
                        request.custom(),
                        request.material(),
                        request.quantity() == null ? 1 : request.quantity())
                .update();
        if (request.custom()) {
            forge.createWorkOrder(id, request.material(), request.quantity() == null ? 1 : request.quantity());
        } else if (inventory.reserve(request.sku(), 1)) {
            markReady(id);
        } else {
            reject(id, "out of stock: " + request.sku());
        }
        return id;
    }

    public CustomerOrder get(UUID id) {
        return jdbc.sql("select id, status, reason from customer_order where id = ?")
                .param(id)
                .query(CustomerOrder.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order " + id));
    }

    public void markReady(UUID id) {
        jdbc.sql("update customer_order set status = 'READY' where id = ?")
                .param(id)
                .update();
    }

    public void reject(UUID id, String reason) {
        jdbc.sql("update customer_order set status = 'REJECTED', reason = ? where id = ?")
                .params(reason, id)
                .update();
    }
}

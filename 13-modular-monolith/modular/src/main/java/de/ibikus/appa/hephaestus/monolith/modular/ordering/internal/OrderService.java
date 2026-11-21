package de.ibikus.appa.hephaestus.monolith.modular.ordering.internal;

import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderRequested;
import de.ibikus.appa.hephaestus.monolith.modular.inventory.Inventory;
import de.ibikus.appa.hephaestus.monolith.modular.ordering.CustomerOrder;
import de.ibikus.appa.hephaestus.monolith.modular.ordering.OrderRequest;
import de.ibikus.appa.hephaestus.monolith.modular.ordering.Orders;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts the order in ordering's schema and asks Forge by event — the transaction ends here, no supplier call
 * inside it. The answer comes back as an event too ({@link ForgeOutcomes}).
 */
@Service
class OrderService implements Orders {

    private final JdbcClient jdbc;
    private final Inventory inventory;
    private final ApplicationEventPublisher events;

    OrderService(JdbcClient jdbc, Inventory inventory, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.events = events;
    }

    @Override
    @Transactional
    public UUID place(OrderRequest request) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                        insert into ordering.customer_order
                            (id, customer_id, sku, custom, material, quantity, status, created_at)
                        values (?, ?, ?, ?, ?, ?, 'ACCEPTED', clock_timestamp())""")
                .params(
                        id,
                        request.customerId(),
                        request.sku(),
                        request.isCustom(),
                        request.material(),
                        request.quantityOrOne())
                .update();
        if (request.isCustom()) {
            events.publishEvent(new WorkOrderRequested(id, request.material(), request.quantityOrOne()));
        } else if (inventory.reserve(request.sku(), 1)) {
            markReady(id);
        } else {
            reject(id, "out of stock: " + request.sku());
        }
        return id;
    }

    @Override
    public CustomerOrder get(UUID id) {
        return jdbc.sql("select id, status, reason from ordering.customer_order where id = ?")
                .param(id)
                .query(CustomerOrder.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order " + id));
    }

    /** Only from ACCEPTED: a repeated outcome (redelivery) changes nothing. */
    void markReady(UUID id) {
        jdbc.sql("update ordering.customer_order set status = 'READY' where id = ? and status = 'ACCEPTED'")
                .param(id)
                .update();
    }

    void reject(UUID id, String reason) {
        jdbc.sql("""
                        update ordering.customer_order set status = 'REJECTED', reason = ?
                        where id = ? and status = 'ACCEPTED'""")
                .params(reason, id)
                .update();
    }
}

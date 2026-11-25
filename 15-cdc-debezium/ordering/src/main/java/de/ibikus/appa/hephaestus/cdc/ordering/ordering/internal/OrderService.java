package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import de.ibikus.appa.hephaestus.cdc.ordering.inventory.Inventory;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.CustomerOrder;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.OrderRequest;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.Orders;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal.QuoteClient.Quoted;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal.QuoteClient.Refused;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts the order in ordering's schema. A custom piece is quoted by Forge first — outside the transaction, so no
 * row is locked while gRPC waits; without a quote the order is REJECTED at once and nothing is sent. With a quote
 * the order is ACCEPTED with price and lead time, and the work order request leaves through the outbox.
 */
@Service
class OrderService implements Orders {

    private final JdbcClient jdbc;
    private final Inventory inventory;
    private final ApplicationEventPublisher events;
    private final QuoteClient quotes;
    private final TransactionTemplate transactions;

    OrderService(
            JdbcClient jdbc,
            Inventory inventory,
            ApplicationEventPublisher events,
            QuoteClient quotes,
            TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.events = events;
        this.quotes = quotes;
        this.transactions = transactions;
    }

    @Override
    public UUID place(OrderRequest request) {
        var id = UUID.randomUUID();
        if (!request.isCustom()) {
            transactions.executeWithoutResult(status -> {
                insert(id, request, "ACCEPTED", null, null);
                if (inventory.reserve(request.sku(), 1)) {
                    markReady(id);
                } else {
                    reject(id, "out of stock: " + request.sku());
                }
            });
            return id;
        }
        switch (quotes.quote(request.material(), request.quantityOrOne())) {
            case Refused refused ->
                transactions.executeWithoutResult(status -> insert(id, request, "REJECTED", refused.reason(), null));
            case Quoted quoted ->
                transactions.executeWithoutResult(status -> {
                    insert(id, request, "ACCEPTED", null, quoted);
                    events.publishEvent(new WorkOrderRequestedEvent(
                            id, request.material(), request.quantityOrOne(), quoted.quoteId(), quoted.priceCents()));
                });
        }
        return id;
    }

    @Override
    public CustomerOrder get(UUID id) {
        return jdbc.sql("""
                        select id, status, reason, price_cents, lead_time_days
                        from ordering.customer_order where id = ?""")
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
                        where id = ? and status = 'ACCEPTED'""").params(reason, id).update();
    }

    private void insert(UUID id, OrderRequest request, String status, String reason, Quoted quote) {
        jdbc.sql("""
                        insert into ordering.customer_order
                            (id, customer_id, sku, custom, material, quantity, status, reason,
                             quote_id, price_cents, lead_time_days, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, clock_timestamp())""")
                .params(
                        id,
                        request.customerId(),
                        request.sku(),
                        request.isCustom(),
                        request.material(),
                        request.quantityOrOne(),
                        status,
                        reason,
                        quote == null ? null : quote.quoteId(),
                        quote == null ? null : quote.priceCents(),
                        quote == null ? null : quote.leadTimeDays())
                .update();
    }
}

package de.ibikus.appa.hephaestus.contracts.forge.application;

import de.ibikus.appa.hephaestus.contracts.forge.domain.SupplyResult.Delivered;
import de.ibikus.appa.hephaestus.contracts.forge.domain.SupplyResult.PermanentFailure;
import de.ibikus.appa.hephaestus.contracts.forge.domain.SupplyResult.TransientFailure;
import de.ibikus.appa.hephaestus.contracts.forge.domain.WorkOrder;
import de.ibikus.appa.hephaestus.contracts.forge.domain.WorkOrder.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Forge's use cases, behind ports (as in 13). */
@Service
public class WorkOrders {

    private final WorkOrderStore store;
    private final MaterialStock stock;
    private final Supplier supplier;
    private final Outcomes outcomes;
    private final Quotes quotes;
    private final TransactionTemplate transactions;

    public WorkOrders(
            WorkOrderStore store,
            MaterialStock stock,
            Supplier supplier,
            Outcomes outcomes,
            Quotes quotes,
            TransactionTemplate transactions) {
        this.store = store;
        this.stock = stock;
        this.supplier = supplier;
        this.outcomes = outcomes;
        this.quotes = quotes;
        this.transactions = transactions;
    }

    /**
     * Once per customer order: a repeated request (Kafka redelivery) changes nothing — the quote is checked only when
     * the work order is created, so a redelivery after expiry does not fail it. {@code quote} is empty for a 1.0.0
     * request: accepted as in 13. A quote that is unknown, expired or does not match fails the work order at once.
     */
    @Transactional
    public void request(UUID orderId, String material, int quantity, Optional<QuoteRef> quote) {
        var workOrder = WorkOrder.requested(orderId, material, quantity);
        if (!store.createIfAbsent(workOrder)) {
            return;
        }
        var verdict = quote.map(ref -> quotes.verify(ref, material, quantity)).orElse(Quotes.Verdict.OK);
        if (verdict != Quotes.Verdict.OK) {
            store.failed(workOrder.id(), verdict.reason());
            outcomes.failed(workOrder, verdict.reason());
            return;
        }
        supply(workOrder.id());
    }

    /** Material from the stock, the shortfall from the supplier; unreachable supplier: left for the retry. */
    void supply(String id) {
        var wo = store.lockWaitingForMaterial(id).orElse(null);
        if (wo == null) {
            return;
        }
        var onHand = stock.lockOnHand(wo.material());
        if (onHand < wo.quantity()) {
            switch (supplier.order(wo.material(), wo.quantity() - onHand)) {
                case Delivered delivered -> stock.add(wo.material(), delivered.quantity());
                case PermanentFailure failure -> {
                    var reason = PermanentFailure.NOT_DELIVERABLE.equals(failure.reason())
                            ? "material not deliverable: " + wo.material()
                            : "unknown material: " + wo.material();
                    store.failed(wo.id(), reason);
                    outcomes.failed(wo, reason);
                    return;
                }
                case TransientFailure ignored -> {
                    return;
                }
            }
        }
        stock.take(wo.material(), wo.quantity());
        store.materialReady(wo.id());
    }

    /** One transaction per waiting work order: one slow supplier answer does not hold the others. */
    public void retryWaitingForMaterial() {
        store.waitingForMaterial().forEach(id -> transactions.executeWithoutResult(status -> supply(id)));
    }

    public List<WorkOrder> list(Status status) {
        return store.list(status);
    }

    @Transactional
    public void start(String id) {
        if (!store.transition(id, Status.PLANNED, Status.IN_PROGRESS, true)) {
            throw refusal(id, "cannot start");
        }
    }

    @Transactional
    public void complete(String id) {
        if (!store.transition(id, Status.IN_PROGRESS, Status.DONE, false)) {
            throw refusal(id, "cannot complete");
        }
        outcomes.completed(store.find(id).orElseThrow());
    }

    private RuntimeException refusal(String id, String what) {
        return store.find(id).isPresent() ? new WorkOrderConflict(id + ": " + what) : new WorkOrderNotFound(id);
    }
}

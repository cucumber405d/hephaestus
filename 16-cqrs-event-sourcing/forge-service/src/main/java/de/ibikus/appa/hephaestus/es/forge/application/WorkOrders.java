package de.ibikus.appa.hephaestus.es.forge.application;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.ConcurrencyConflict;
import de.ibikus.appa.hephaestus.es.forge.domain.SupplyResult.Delivered;
import de.ibikus.appa.hephaestus.es.forge.domain.SupplyResult.PermanentFailure;
import de.ibikus.appa.hephaestus.es.forge.domain.SupplyResult.TransientFailure;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialCommand.Receive;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialCommand.Take;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialDecider;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived.Source;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Allocate;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Complete;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Fail;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Request;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Start;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderDecider;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderState.Status;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Forge's use cases on the journal: load the stream, decide, append. Everything a command changes is appended in its
 * transaction; the ForgeEvent for Ordering is derived from the journal afterwards (the publisher), and the smith's
 * list is a projection.
 */
@Service
public class WorkOrders {

    private static final Logger log = LoggerFactory.getLogger(WorkOrders.class);

    private final WorkOrderRepository workOrders;
    private final MaterialRepository materials;
    private final WorkshopBoard board;
    private final Supplier supplier;
    private final Quotes quotes;
    private final TransactionTemplate transactions;

    public WorkOrders(
            WorkOrderRepository workOrders,
            MaterialRepository materials,
            WorkshopBoard board,
            Supplier supplier,
            Quotes quotes,
            TransactionTemplate transactions) {
        this.workOrders = workOrders;
        this.materials = materials;
        this.board = board;
        this.supplier = supplier;
        this.quotes = quotes;
        this.transactions = transactions;
    }

    /**
     * Once per customer order: the stream id follows from the order, so a repeated request (Kafka redelivery) finds
     * the stream and appends nothing. {@code quote} is empty for a 1.0.0 request (accepted as in 13); a quote that is
     * unknown, expired or does not match fails the work order at once — checked only when the stream is created.
     */
    @Transactional
    public void request(UUID orderId, String material, int quantity, Optional<QuoteRef> quote) {
        var id = WorkOrderDecider.idFor(orderId);
        var loaded = workOrders.load(id);
        var events = new ArrayList<>(WorkOrderDecider.decide(new Request(orderId, material, quantity), loaded.state()));
        if (events.isEmpty()) {
            return;
        }
        var verdict = quote.map(ref -> quotes.verify(ref, material, quantity)).orElse(Quotes.Verdict.OK);
        if (verdict != Quotes.Verdict.OK) {
            var requested = WorkOrderDecider.fold(events);
            events.addAll(WorkOrderDecider.decide(new Fail(verdict.reason()), requested));
        }
        workOrders.save(id, loaded, events, metadata(orderId));
        if (verdict == Quotes.Verdict.OK) {
            supply(id);
        }
    }

    /**
     * Material from the stock, the shortfall from the supplier; both streams (material, work order) in the caller's
     * transaction. An unreachable supplier leaves the work order waiting for the retry.
     */
    void supply(String id) {
        var wo = workOrders.load(id);
        if (wo.state().status() != Status.WAITING_FOR_MATERIAL) {
            return;
        }
        var state = wo.state();
        var meta = metadata(state.orderId());
        var material = materials.load(state.material());
        if (material.state().onHand() < state.quantity()) {
            switch (supplier.order(state.material(), state.quantity() - material.state().onHand())) {
                case Delivered delivered ->
                    material = materials.save(
                            material,
                            MaterialDecider.decide(new Receive(delivered.quantity(), Source.SUPPLIER), material.state()),
                            meta);
                case PermanentFailure failure -> {
                    var reason = PermanentFailure.NOT_DELIVERABLE.equals(failure.reason())
                            ? "material not deliverable: " + state.material()
                            : "unknown material: " + state.material();
                    workOrders.save(id, wo, WorkOrderDecider.decide(new Fail(reason), state), meta);
                    return;
                }
                case TransientFailure ignored -> {
                    return;
                }
            }
        }
        materials.save(material, MaterialDecider.decide(new Take(state.quantity(), id), material.state()), meta);
        workOrders.save(id, wo, WorkOrderDecider.decide(new Allocate(), state), meta);
    }

    /** One transaction per waiting work order (from the board): one slow supplier answer does not hold the others. */
    public void retryWaitingForMaterial() {
        for (var id : board.waitingForMaterial()) {
            try {
                transactions.executeWithoutResult(status -> supply(id));
            } catch (ConcurrencyConflict e) {
                log.info("supply of {} raced another writer; retried on the next round", id);
            }
        }
    }

    @Transactional
    public void start(String id, String smith) {
        decideAndSave(id, new Start(smith));
    }

    @Transactional
    public void complete(String id) {
        decideAndSave(id, new Complete());
    }

    /** The material's stock as the journal said at {@code at}. */
    public int stockAt(String material, Instant at) {
        return materials.onHandAt(material, at);
    }

    private void decideAndSave(String id, WorkOrderCommand command) {
        var loaded = workOrders.load(id);
        if (loaded.state().status() == Status.NEW) {
            throw new WorkOrderNotFound(id);
        }
        var events = WorkOrderDecider.decide(command, loaded.state());
        workOrders.save(id, loaded, events, metadata(loaded.state().orderId()));
    }

    private static Map<String, String> metadata(UUID orderId) {
        return Map.of("correlationId", String.valueOf(orderId));
    }
}

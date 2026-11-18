package de.ibikus.appa.hephaestus.observability.ordering.orders;

import static de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage.*;

import de.ibikus.appa.hephaestus.observability.ordering.admin.Levers;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.Outbox;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.ProcessedMessages;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.SagaMessage.Line;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.Topics;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.TraceCarrier;
import de.ibikus.appa.hephaestus.observability.ordering.orders.Orders.Order;
import de.ibikus.appa.hephaestus.observability.ordering.saga.Orchestrator;
import de.ibikus.appa.hephaestus.observability.ordering.saga.Sagas;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places orders and follows their saga. Choreography: Ordering only starts it (OrderPlaced) and watches the other
 * services' events. Orchestration: Ordering decides every step ({@link Orchestrator}), sends the commands, waits
 * for replies with a deadline. Every state change and the messages it causes share one transaction (outbox).
 */
@Service
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

    /** Prices of the shop (EUR); Inventory knows the stock, Ordering the prices. */
    public static final Map<String, BigDecimal> PRICES = Map.of(
            "ANVIL-01", new BigDecimal("120.00"),
            "HAMMER-01", new BigDecimal("35.00"),
            "TONGS-01", new BigDecimal("18.50"));

    private final Orders orders;
    private final Sagas sagas;
    private final Outbox outbox;
    private final Messages messages;
    private final ProcessedMessages processed;
    private final Levers levers;
    private final Clock clock;
    private final Duration stepTimeout;
    private final TraceCarrier carrier;
    private final SagaMetrics metrics;

    public OrderService(
            Orders orders,
            Sagas sagas,
            Outbox outbox,
            Messages messages,
            ProcessedMessages processed,
            Levers levers,
            Clock clock,
            @Value("${saga.step-timeout}") Duration stepTimeout,
            TraceCarrier carrier,
            SagaMetrics metrics) {
        this.orders = orders;
        this.sagas = sagas;
        this.outbox = outbox;
        this.messages = messages;
        this.processed = processed;
        this.levers = levers;
        this.clock = clock;
        this.stepTimeout = stepTimeout;
        this.carrier = carrier;
        this.metrics = metrics;
    }

    @Transactional
    public Order place(String customerId, String saga, List<Line> lines) {
        if (!CHOREOGRAPHY.equals(saga) && !ORCHESTRATION.equals(saga)) {
            throw new IllegalArgumentException("saga must be choreography or orchestration");
        }
        // The same SKU twice counts once, quantities added up (one line per SKU everywhere downstream)
        var merged = new java.util.TreeMap<String, Integer>();
        lines.forEach(line -> merged.merge(line.sku(), line.quantity(), Integer::sum));
        lines = merged.entrySet().stream()
                .map(e -> new Line(e.getKey(), e.getValue()))
                .toList();
        var amount = BigDecimal.ZERO;
        for (var line : lines) {
            var price = PRICES.get(line.sku());
            if (price == null || line.quantity() < 1) {
                throw new IllegalArgumentException("Unknown SKU or bad quantity: " + line.sku());
            }
            amount = amount.add(price.multiply(BigDecimal.valueOf(line.quantity())));
        }
        var order = new Order(UUID.randomUUID(), customerId, saga, "PENDING", amount, null, List.copyOf(lines));
        // The request's span is found by the order too: a trace the outbox broke shows up as a second trace
        carrier.tag("order.id", order.id().toString());
        carrier.tag("saga.style", saga);
        var now = clock.instant();
        SagaMessage first;
        String topic;
        if (CHOREOGRAPHY.equals(saga)) {
            first = Orders.message(order, ORDER_PLACED, null);
            topic = Topics.ORDERING_EVENTS;
        } else {
            first = Orders.message(order, Orchestrator.firstCommand(), null);
            topic = Orchestrator.topicOf(first.type());
        }
        orders.insert(order, topic, messages.write(first), now);
        orders.history(order.id(), "placed (" + saga + "), " + amount + " EUR");
        if (ORCHESTRATION.equals(saga)) {
            sagas.insert(order.id(), Orchestrator.start(), now.plus(stepTimeout), carrier.capture());
        }
        outbox.send(topic, first);
        orders.history(order.id(), "→ " + first.type());
        return order;
    }

    /** Choreography: another service's event about one of our orders. */
    @Transactional
    public void onEvent(SagaMessage event) {
        if (!event.choreography() || !firstTime(event)) {
            return;
        }
        metrics.stepDone(event.saga(), event.type(), lastEntryAt(event.orderId()));
        orders.history(event.orderId(), "← " + event.type() + reasonSuffix(event.reason()));
        switch (event.type()) {
            case DELIVERY_SCHEDULED -> settle(event, "CONFIRMED", null);
            case PAYMENT_DECLINED, STOCK_UNAVAILABLE, DELIVERY_FAILED -> settle(event, "REJECTED", event.reason());
            default -> {
                // progress or compensation: history only
            }
        }
    }

    /** Orchestration: a participant's reply. */
    @Transactional
    public void onReply(SagaMessage reply) {
        if (!reply.orchestration() || !firstTime(reply)) {
            return;
        }
        metrics.stepDone(reply.saga(), reply.type(), lastEntryAt(reply.orderId()));
        orders.history(reply.orderId(), "← " + reply.type() + reasonSuffix(reply.reason()));
        var current = sagas.lock(reply.orderId());
        if (current.isEmpty()) {
            return;
        }
        var decision = Orchestrator.onReply(current.get(), reply.type(), reply.reason());
        if (decision.ignored(current.get())) {
            orders.history(
                    reply.orderId(),
                    "  (ignored: saga is " + current.get().state().name().toLowerCase() + ")");
            return;
        }
        apply(reply.orderId(), decision);
    }

    /** Orchestration: steps that did not answer in time are compensated (called by {@link SagaTimeouts}). */
    @Transactional
    public void timeouts() {
        for (var orderId : sagas.overdue(clock.instant())) {
            // The scheduler has its own trace: continue the saga's instead, so the compensation is part of it
            carrier.continueTrace(sagas.traceparent(orderId).orElse(null), "saga timeout", () -> {
                carrier.tag("order.id", orderId.toString());
                var current = sagas.lock(orderId).orElseThrow();
                var decision = Orchestrator.onTimeout(current);
                metrics.timedOut(current.step().name().toLowerCase());
                orders.history(orderId, "⏱ " + decision.reason());
                LOG.warn("Saga of order {}: {}", orderId, decision.reason());
                apply(orderId, decision);
                return null;
            });
        }
    }

    /** For the duplicate experiment: send the saga's first message again, same message id. */
    @Transactional
    public void republish(UUID orderId) {
        var first = orders.firstMessage(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        var message = messages.read(first[1]);
        outbox.send(first[0], message);
        orders.history(orderId, "→ " + message.type() + " (re-sent, same message id)");
    }

    private void apply(UUID orderId, Orchestrator.Decision decision) {
        var order = orders.find(orderId).orElseThrow();
        carrier.tag("saga.step", decision.next().step().name().toLowerCase());
        sagas.update(orderId, decision.next(), clock.instant().plus(stepTimeout), carrier.capture());
        if (decision.outcome() != null
                && orders.settle(orderId, decision.outcome().name(), decision.reason())) {
            orders.history(orderId, "order " + decision.outcome().name());
            metrics.settled(order.saga(), decision.outcome().name(), decision.reason(), placedAt(orderId));
        }
        for (var command : decision.commands()) {
            var message = Orders.message(order, command, decision.reason());
            outbox.send(Orchestrator.topicOf(command), message);
            orders.history(orderId, "→ " + command);
        }
    }

    private void settle(SagaMessage event, String status, String reason) {
        if (orders.settle(event.orderId(), status, reason)) {
            orders.history(event.orderId(), "order " + status);
            metrics.settled(event.saga(), status, reason, placedAt(event.orderId()));
        }
    }

    private boolean firstTime(SagaMessage message) {
        if (processed.firstTime(message.messageId())) {
            levers.handled();
            return true;
        }
        levers.duplicate();
        return false;
    }

    /** When the order was placed: its first history entry. */
    private Instant placedAt(UUID orderId) {
        return orders.historyOf(orderId).getFirst().at();
    }

    /** When the current step started: the latest history entry so far. */
    private Instant lastEntryAt(UUID orderId) {
        return orders.historyOf(orderId).getLast().at();
    }

    private static String reasonSuffix(String reason) {
        return reason == null ? "" : " (" + reason + ")";
    }
}

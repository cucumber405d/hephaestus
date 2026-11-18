package de.ibikus.appa.hephaestus.outboxsaga.inventory.stock;

import static de.ibikus.appa.hephaestus.outboxsaga.inventory.messaging.SagaMessage.*;

import de.ibikus.appa.hephaestus.outboxsaga.inventory.admin.Levers;
import de.ibikus.appa.hephaestus.outboxsaga.inventory.messaging.Outbox;
import de.ibikus.appa.hephaestus.outboxsaga.inventory.messaging.ProcessedMessages;
import de.ibikus.appa.hephaestus.outboxsaga.inventory.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.outboxsaga.inventory.messaging.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inventory's part in both saga styles (see PaymentHandler for the pattern). */
@Service
public class InventoryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryHandler.class);

    private final Stock stock;
    private final ProcessedMessages processed;
    private final Outbox outbox;
    private final Levers levers;

    public InventoryHandler(Stock stock, ProcessedMessages processed, Outbox outbox, Levers levers) {
        this.stock = stock;
        this.processed = processed;
        this.outbox = outbox;
        this.levers = levers;
    }

    @Transactional
    public void handle(SagaMessage message) {
        if (!relevant(message)) {
            return;
        }
        if (!processed.firstTime(message.messageId())) {
            levers.duplicate();
            LOG.info("Duplicate {} {} for order {}: skipped", message.type(), message.messageId(), message.orderId());
            return;
        }
        levers.delay();
        levers.handled();
        switch (message.type()) {
            case PAYMENT_RESERVED -> outbox.send(Topics.INVENTORY_EVENTS, reserve(message));
            case RESERVE_STOCK -> outbox.send(Topics.ORDERING_REPLIES, reserve(message));
            case DELIVERY_FAILED -> {
                if (stock.release(message.orderId())) {
                    outbox.send(Topics.INVENTORY_EVENTS, message.next(STOCK_RELEASED, message.type()));
                }
            }
            case RELEASE_STOCK -> {
                stock.release(message.orderId());
                outbox.send(Topics.ORDERING_REPLIES, message.next(STOCK_RELEASED, message.reason()));
            }
            default -> throw new IllegalStateException("Unexpected " + message.type());
        }
    }

    static boolean relevant(SagaMessage message) {
        return switch (message.type()) {
            case PAYMENT_RESERVED, DELIVERY_FAILED -> message.choreography();
            case RESERVE_STOCK, RELEASE_STOCK -> message.orchestration();
            default -> false;
        };
    }

    private SagaMessage reserve(SagaMessage message) {
        if (levers.failsFor(message.customerId())) {
            return message.next(STOCK_UNAVAILABLE, "warehouse refused (injected)");
        }
        var outcome = stock.reserve(message.orderId(), message.lines());
        LOG.info("Stock {} for order {}", outcome, message.orderId());
        return outcome == Stock.Outcome.RESERVED
                ? message.next(STOCK_RESERVED, null)
                : message.next(STOCK_UNAVAILABLE, "not enough stock");
    }
}

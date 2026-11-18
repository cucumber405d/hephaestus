package de.ibikus.appa.hephaestus.outboxsaga.delivery.deliveries;

import static de.ibikus.appa.hephaestus.outboxsaga.delivery.messaging.SagaMessage.*;

import de.ibikus.appa.hephaestus.outboxsaga.delivery.admin.Levers;
import de.ibikus.appa.hephaestus.outboxsaga.delivery.messaging.Outbox;
import de.ibikus.appa.hephaestus.outboxsaga.delivery.messaging.ProcessedMessages;
import de.ibikus.appa.hephaestus.outboxsaga.delivery.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.outboxsaga.delivery.messaging.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery's part in both saga styles (see PaymentHandler for the pattern). It fails for the customers set in
 * /admin/faults ("no courier in the region"). Only the orchestrator can cancel: in the choreography nobody tells
 * Delivery that an order it never saw was abandoned.
 */
@Service
public class DeliveryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryHandler.class);

    private final Deliveries deliveries;
    private final ProcessedMessages processed;
    private final Outbox outbox;
    private final Levers levers;

    public DeliveryHandler(Deliveries deliveries, ProcessedMessages processed, Outbox outbox, Levers levers) {
        this.deliveries = deliveries;
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
            case STOCK_RESERVED -> outbox.send(Topics.DELIVERY_EVENTS, schedule(message));
            case SCHEDULE_DELIVERY -> outbox.send(Topics.ORDERING_REPLIES, schedule(message));
            case CANCEL_DELIVERY -> {
                deliveries.cancel(message.orderId(), message.customerId());
                outbox.send(Topics.ORDERING_REPLIES, message.next(DELIVERY_CANCELLED, message.reason()));
            }
            default -> throw new IllegalStateException("Unexpected " + message.type());
        }
    }

    static boolean relevant(SagaMessage message) {
        return switch (message.type()) {
            case STOCK_RESERVED -> message.choreography();
            case SCHEDULE_DELIVERY, CANCEL_DELIVERY -> message.orchestration();
            default -> false;
        };
    }

    private SagaMessage schedule(SagaMessage message) {
        var outcome = deliveries.schedule(
                message.orderId(), message.customerId(), !levers.failsFor(message.customerId()));
        LOG.info("Delivery {} for order {}", outcome, message.orderId());
        return outcome == Deliveries.Outcome.SCHEDULED
                ? message.next(DELIVERY_SCHEDULED, null)
                : message.next(DELIVERY_FAILED, "no courier in the region");
    }
}

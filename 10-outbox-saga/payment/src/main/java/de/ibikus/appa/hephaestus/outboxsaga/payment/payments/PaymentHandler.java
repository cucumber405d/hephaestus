package de.ibikus.appa.hephaestus.outboxsaga.payment.payments;

import static de.ibikus.appa.hephaestus.outboxsaga.payment.messaging.SagaMessage.*;

import de.ibikus.appa.hephaestus.outboxsaga.payment.admin.Levers;
import de.ibikus.appa.hephaestus.outboxsaga.payment.messaging.Outbox;
import de.ibikus.appa.hephaestus.outboxsaga.payment.messaging.ProcessedMessages;
import de.ibikus.appa.hephaestus.outboxsaga.payment.messaging.SagaMessage;
import de.ibikus.appa.hephaestus.outboxsaga.payment.messaging.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment's part in both saga styles. The action is the same; the style decides the trigger and the answer:
 * choreography reacts to other services' events and publishes its own event, orchestration obeys commands and
 * replies to the orchestrator. Idempotency check, state change and outgoing message share one transaction.
 */
@Service
public class PaymentHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentHandler.class);

    private final Payments payments;
    private final ProcessedMessages processed;
    private final Outbox outbox;
    private final Levers levers;

    public PaymentHandler(Payments payments, ProcessedMessages processed, Outbox outbox, Levers levers) {
        this.payments = payments;
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
            case ORDER_PLACED -> outbox.send(Topics.PAYMENT_EVENTS, reserve(message));
            case RESERVE_PAYMENT -> outbox.send(Topics.ORDERING_REPLIES, reserve(message));
            case STOCK_UNAVAILABLE, DELIVERY_FAILED -> {
                if (payments.refund(message.orderId(), message.customerId())) {
                    outbox.send(Topics.PAYMENT_EVENTS, message.next(PAYMENT_REFUNDED, message.type()));
                }
            }
            // The orchestrator waits for the confirmation, even if there was nothing to give back
            case REFUND_PAYMENT -> {
                payments.refund(message.orderId(), message.customerId());
                outbox.send(Topics.ORDERING_REPLIES, message.next(PAYMENT_REFUNDED, message.reason()));
            }
            default -> throw new IllegalStateException("Unexpected " + message.type());
        }
    }

    /** Which messages Payment acts on, per style; everything else on the subscribed topics is not its business. */
    static boolean relevant(SagaMessage message) {
        return switch (message.type()) {
            case ORDER_PLACED, STOCK_UNAVAILABLE, DELIVERY_FAILED -> message.choreography();
            case RESERVE_PAYMENT, REFUND_PAYMENT -> message.orchestration();
            default -> false;
        };
    }

    private SagaMessage reserve(SagaMessage message) {
        if (levers.failsFor(message.customerId())) {
            return message.next(PAYMENT_DECLINED, "payment provider refused (injected)");
        }
        var outcome = payments.reserve(message.orderId(), message.customerId(), message.amount());
        LOG.info("Payment {} for order {} ({} EUR)", outcome, message.orderId(), message.amount());
        return outcome == Payments.Outcome.RESERVED
                ? message.next(PAYMENT_RESERVED, null)
                : message.next(PAYMENT_DECLINED, "credit limit exceeded");
    }
}

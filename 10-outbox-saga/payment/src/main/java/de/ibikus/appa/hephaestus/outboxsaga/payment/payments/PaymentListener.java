package de.ibikus.appa.hephaestus.outboxsaga.payment.payments;

import de.ibikus.appa.hephaestus.outboxsaga.payment.messaging.Messages;
import de.ibikus.appa.hephaestus.outboxsaga.payment.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Everything Payment may have to react to: the order's start, later failures (choreography) and its commands. */
@Component
class PaymentListener {

    private final Messages messages;
    private final PaymentHandler handler;

    PaymentListener(Messages messages, PaymentHandler handler) {
        this.messages = messages;
        this.handler = handler;
    }

    @KafkaListener(
            id = "payment",
            topics = {Topics.ORDERING_EVENTS, Topics.INVENTORY_EVENTS, Topics.DELIVERY_EVENTS, Topics.PAYMENT_COMMANDS})
    void on(String value) {
        handler.handle(messages.read(value));
    }
}

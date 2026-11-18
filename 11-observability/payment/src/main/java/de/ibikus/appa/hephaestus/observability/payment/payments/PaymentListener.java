package de.ibikus.appa.hephaestus.observability.payment.payments;

import de.ibikus.appa.hephaestus.observability.payment.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.payment.messaging.TraceCarrier;
import de.ibikus.appa.hephaestus.observability.payment.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Everything Payment may have to react to: the order's start, later failures (choreography) and its commands. */
@Component
class PaymentListener {

    private final Messages messages;
    private final PaymentHandler handler;
    private final TraceCarrier carrier;

    PaymentListener(Messages messages, PaymentHandler handler, TraceCarrier carrier) {
        this.messages = messages;
        this.handler = handler;
        this.carrier = carrier;
    }

    @KafkaListener(
            id = "payment",
            topics = {Topics.ORDERING_EVENTS, Topics.INVENTORY_EVENTS, Topics.DELIVERY_EVENTS, Topics.PAYMENT_COMMANDS})
    void on(String value) {
        var message = messages.read(value);
        carrier.tag(message);
        handler.handle(message);
    }
}

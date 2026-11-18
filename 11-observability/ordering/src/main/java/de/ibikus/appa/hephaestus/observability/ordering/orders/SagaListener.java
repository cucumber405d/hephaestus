package de.ibikus.appa.hephaestus.observability.ordering.orders;

import de.ibikus.appa.hephaestus.observability.ordering.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.Topics;
import de.ibikus.appa.hephaestus.observability.ordering.messaging.TraceCarrier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Replies to the orchestrator, and the participants' events for the choreography. */
@Component
class SagaListener {

    private final Messages messages;
    private final OrderService orders;
    private final TraceCarrier carrier;

    SagaListener(Messages messages, OrderService orders, TraceCarrier carrier) {
        this.messages = messages;
        this.orders = orders;
        this.carrier = carrier;
    }

    @KafkaListener(
            id = "ordering",
            topics = {Topics.ORDERING_REPLIES, Topics.PAYMENT_EVENTS, Topics.INVENTORY_EVENTS, Topics.DELIVERY_EVENTS})
    void on(String value, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        var message = messages.read(value);
        carrier.tag(message);
        if (Topics.ORDERING_REPLIES.equals(topic)) {
            orders.onReply(message);
        } else {
            orders.onEvent(message);
        }
    }
}

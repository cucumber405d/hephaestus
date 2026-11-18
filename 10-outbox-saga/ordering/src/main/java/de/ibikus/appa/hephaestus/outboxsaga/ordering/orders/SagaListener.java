package de.ibikus.appa.hephaestus.outboxsaga.ordering.orders;

import de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.Messages;
import de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Replies to the orchestrator, and the participants' events for the choreography. */
@Component
class SagaListener {

    private final Messages messages;
    private final OrderService orders;

    SagaListener(Messages messages, OrderService orders) {
        this.messages = messages;
        this.orders = orders;
    }

    @KafkaListener(
            id = "ordering",
            topics = {Topics.ORDERING_REPLIES, Topics.PAYMENT_EVENTS, Topics.INVENTORY_EVENTS, Topics.DELIVERY_EVENTS})
    void on(String value, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        var message = messages.read(value);
        if (Topics.ORDERING_REPLIES.equals(topic)) {
            orders.onReply(message);
        } else {
            orders.onEvent(message);
        }
    }
}

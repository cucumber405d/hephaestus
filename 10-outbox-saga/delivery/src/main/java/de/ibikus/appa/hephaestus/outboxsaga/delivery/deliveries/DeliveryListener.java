package de.ibikus.appa.hephaestus.outboxsaga.delivery.deliveries;

import de.ibikus.appa.hephaestus.outboxsaga.delivery.messaging.Messages;
import de.ibikus.appa.hephaestus.outboxsaga.delivery.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Stock reserved (choreography) and its commands. */
@Component
class DeliveryListener {

    private final Messages messages;
    private final DeliveryHandler handler;

    DeliveryListener(Messages messages, DeliveryHandler handler) {
        this.messages = messages;
        this.handler = handler;
    }

    @KafkaListener(
            id = "delivery",
            topics = {Topics.INVENTORY_EVENTS, Topics.DELIVERY_COMMANDS})
    void on(String value) {
        handler.handle(messages.read(value));
    }
}

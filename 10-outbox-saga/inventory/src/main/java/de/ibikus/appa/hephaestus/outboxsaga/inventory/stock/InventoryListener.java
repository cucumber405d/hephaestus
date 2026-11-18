package de.ibikus.appa.hephaestus.outboxsaga.inventory.stock;

import de.ibikus.appa.hephaestus.outboxsaga.inventory.messaging.Messages;
import de.ibikus.appa.hephaestus.outboxsaga.inventory.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Everything Inventory may have to react to: payment done, delivery failed (choreography) and its commands. */
@Component
class InventoryListener {

    private final Messages messages;
    private final InventoryHandler handler;

    InventoryListener(Messages messages, InventoryHandler handler) {
        this.messages = messages;
        this.handler = handler;
    }

    @KafkaListener(
            id = "inventory",
            topics = {Topics.PAYMENT_EVENTS, Topics.DELIVERY_EVENTS, Topics.INVENTORY_COMMANDS})
    void on(String value) {
        handler.handle(messages.read(value));
    }
}

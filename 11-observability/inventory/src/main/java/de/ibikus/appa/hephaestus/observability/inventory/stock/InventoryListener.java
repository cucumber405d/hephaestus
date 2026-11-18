package de.ibikus.appa.hephaestus.observability.inventory.stock;

import de.ibikus.appa.hephaestus.observability.inventory.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.inventory.messaging.TraceCarrier;
import de.ibikus.appa.hephaestus.observability.inventory.messaging.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Everything Inventory may have to react to: payment done, delivery failed (choreography) and its commands. */
@Component
class InventoryListener {

    private final Messages messages;
    private final InventoryHandler handler;
    private final TraceCarrier carrier;

    InventoryListener(Messages messages, InventoryHandler handler, TraceCarrier carrier) {
        this.messages = messages;
        this.handler = handler;
        this.carrier = carrier;
    }

    @KafkaListener(
            id = "inventory",
            topics = {Topics.PAYMENT_EVENTS, Topics.DELIVERY_EVENTS, Topics.INVENTORY_COMMANDS})
    void on(String value) {
        var message = messages.read(value);
        carrier.tag(message);
        handler.handle(message);
    }
}

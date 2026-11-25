package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import de.ibikus.appa.hephaestus.contracts.events.WorkOrderRequest;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The outbox relay: after the order's transaction commits, sends the Avro {@code WorkOrderRequest} (1.1.0, with the
 * quote) to cdc.forge.requests, key = customer order, and waits for the broker's ack; until then the publication stays
 * incomplete and is republished on restart.
 */
@Component
class RequestRelay {

    private final KafkaTemplate<String, Object> kafka;

    RequestRelay(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @ApplicationModuleListener
    void on(WorkOrderRequestedEvent event) throws Exception {
        var request = WorkOrderRequest.newBuilder()
                .setOrderId(event.orderId().toString())
                .setMaterial(event.material())
                .setQuantity(event.quantity())
                .setQuoteId(event.quoteId())
                .setPriceCents(event.priceCents())
                .build();
        kafka.send("cdc.forge.requests", event.orderId().toString(), request).get(10, TimeUnit.SECONDS);
    }
}

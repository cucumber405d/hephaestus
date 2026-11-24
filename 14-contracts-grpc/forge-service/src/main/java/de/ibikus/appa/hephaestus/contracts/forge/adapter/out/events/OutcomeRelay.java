package de.ibikus.appa.hephaestus.contracts.forge.adapter.out.events;

import de.ibikus.appa.hephaestus.contracts.events.ForgeEvent;
import de.ibikus.appa.hephaestus.contracts.events.ForgeEventType;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The outbox relay: after the work order's transaction commits, maps the outcome to the Avro {@code ForgeEvent} and
 * sends it to forge.events (key = customer order), waiting for the broker's ack. Until this method returns, the
 * publication stays incomplete in the registry and is republished on restart — in 13 {@code @Externalized} did
 * this, but it would have put the Avro class into the registry's JSON.
 */
@Component
class OutcomeRelay {

    private final KafkaTemplate<String, Object> kafka;

    OutcomeRelay(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @ApplicationModuleListener
    void on(WorkOrderOutcome outcome) throws Exception {
        var event = ForgeEvent.newBuilder()
                .setEventId(outcome.eventId().toString())
                .setType(
                        outcome.kind() == WorkOrderOutcome.Kind.COMPLETED
                                ? ForgeEventType.WORK_ORDER_COMPLETED
                                : ForgeEventType.WORK_ORDER_FAILED)
                .setOrderId(outcome.orderId().toString())
                .setWorkOrderId(outcome.workOrderId())
                .setReason(outcome.reason())
                .build();
        kafka.send("forge.events", outcome.orderId().toString(), event).get(10, TimeUnit.SECONDS);
    }
}

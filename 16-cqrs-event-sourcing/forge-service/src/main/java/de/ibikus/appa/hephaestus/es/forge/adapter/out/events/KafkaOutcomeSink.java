package de.ibikus.appa.hephaestus.es.forge.adapter.out.events;

import de.ibikus.appa.hephaestus.contracts.events.ForgeEvent;
import de.ibikus.appa.hephaestus.contracts.events.ForgeEventType;
import de.ibikus.appa.hephaestus.es.forge.application.Outcome;
import de.ibikus.appa.hephaestus.es.forge.application.OutcomeSink;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Outcome → Avro {@code ForgeEvent} on es.forge.events, key = customer order; waits up to 10 s for the ack. */
@Component
class KafkaOutcomeSink implements OutcomeSink {

    private final KafkaTemplate<String, Object> kafka;

    KafkaOutcomeSink(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void send(Outcome outcome) {
        var event = ForgeEvent.newBuilder()
                .setEventId(outcome.eventId().toString())
                .setType(
                        outcome.kind() == Outcome.Kind.COMPLETED
                                ? ForgeEventType.WORK_ORDER_COMPLETED
                                : ForgeEventType.WORK_ORDER_FAILED)
                .setOrderId(outcome.orderId().toString())
                .setWorkOrderId(outcome.workOrderId())
                .setReason(outcome.reason())
                .build();
        try {
            kafka.send("es.forge.events", outcome.orderId().toString(), event).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while sending " + outcome.eventId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("es.forge.events did not acknowledge " + outcome.eventId(), e);
        }
    }
}

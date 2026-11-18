package de.ibikus.appa.hephaestus.observability.payment.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends the outbox to Kafka, in place of Spring Modulith's Kafka externalizer (Delivery still uses that one):
 * the same publication registry stores the message with the business transaction and calls this listener after the
 * commit; a failed send leaves the publication incomplete and the resubmitter calls it again. The difference: the
 * send runs as a child of the trace stored in the message, so a message resent minutes later is still in its saga's
 * trace (the externalizer would take the scheduler thread's context, i.e. none).
 */
@Component
public class OutboxRelay {

    private final KafkaTemplate<String, String> kafka;
    private final Messages messages;
    private final TraceCarrier carrier;

    public OutboxRelay(KafkaTemplate<String, String> kafka, Messages messages, TraceCarrier carrier) {
        this.kafka = kafka;
        this.messages = messages;
        this.carrier = carrier;
    }

    @ApplicationModuleListener
    public void relay(OutgoingMessage outgoing) {
        carrier.continueTrace(outgoing.traceparent(), "outbox relay", () -> send(outgoing));
    }

    private Object send(OutgoingMessage outgoing) {
        var message = outgoing.message();
        try {
            return kafka.send(outgoing.topic(), message.orderId().toString(), messages.write(message))
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending to " + outgoing.topic(), e);
        } catch (ExecutionException | TimeoutException e) {
            // The publication stays incomplete; OutboxConfiguration resubmits it
            throw new IllegalStateException("Kafka send to " + outgoing.topic() + " failed", e);
        }
    }
}

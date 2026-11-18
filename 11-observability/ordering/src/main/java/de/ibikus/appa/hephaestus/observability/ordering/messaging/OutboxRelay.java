package de.ibikus.appa.hephaestus.observability.ordering.messaging;

import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the outbox to Kafka, in place of Spring Modulith's Kafka externalizer (Delivery still uses that one), the
 * way the externalizer does it: a listener after the commit that returns the send as a future; Modulith's
 * publication registry marks the publication complete when the future succeeds and keeps it incomplete when it
 * fails, so the resubmitter calls this listener again. The difference: the send runs as a child of the trace stored
 * in the message, so a message resent minutes later is still in its saga's trace.
 *
 * <p>One sending thread, in publication order: an order's messages reach Kafka in the order they were written, also
 * when a burst is resubmitted after an outage; no database connection is held while Kafka is slow. The current
 * context is handed to that thread too, so with carrying switched off the first send still joins the request's
 * trace (what the externalizer does out of the box) and only a resubmission starts a new one.
 */
@Component
public class OutboxRelay implements DisposableBean {

    private static final ContextSnapshotFactory SNAPSHOTS =
            ContextSnapshotFactory.builder().build();

    private final KafkaTemplate<String, String> kafka;
    private final Messages messages;
    private final TraceCarrier carrier;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(Thread.ofPlatform()
            .name("outbox-relay")
            .daemon(true)
            .factory());

    public OutboxRelay(KafkaTemplate<String, String> kafka, Messages messages, TraceCarrier carrier) {
        this.kafka = kafka;
        this.messages = messages;
        this.carrier = carrier;
    }

    @TransactionalEventListener
    public CompletableFuture<Object> relay(OutgoingMessage outgoing) {
        var snapshot = SNAPSHOTS.captureAll();
        return CompletableFuture.supplyAsync(
                () -> {
                    try (var scope = snapshot.setThreadLocals()) {
                        return carrier.continueTrace(outgoing.traceparent(), "outbox relay", () -> {
                            carrier.tag(outgoing.message());
                            return send(outgoing);
                        });
                    }
                },
                sender);
    }

    private Object send(OutgoingMessage outgoing) {
        var message = outgoing.message();
        try {
            return kafka.send(outgoing.topic(), message.orderId().toString(), messages.write(message))
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Interrupted while sending to " + outgoing.topic(), e);
        } catch (ExecutionException | TimeoutException e) {
            // The publication stays incomplete; OutboxConfiguration resubmits it
            throw new CompletionException("Kafka send to " + outgoing.topic() + " failed", e);
        }
    }

    @Override
    public void destroy() {
        sender.shutdown();
    }
}

package de.ibikus.appa.hephaestus.eventskafka.notification.domain;

import java.time.Instant;
import java.util.UUID;

/** A message sent to a customer (here: stored and logged; a real channel would be e-mail or push). */
public record Notification(UUID id, String customerId, UUID orderId, String channel, String text, Instant createdAt) {

    public static Notification orderConfirmed(OrderPlaced event, Instant now) {
        var text = "Hello %s, your order %s with %d item(s) is confirmed."
                .formatted(event.customerId(), event.orderId(), event.itemCount());
        return new Notification(UUID.randomUUID(), event.customerId(), event.orderId(), "EMAIL", text, now);
    }
}

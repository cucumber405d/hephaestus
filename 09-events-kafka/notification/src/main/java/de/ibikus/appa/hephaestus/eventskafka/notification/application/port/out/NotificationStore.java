package de.ibikus.appa.hephaestus.eventskafka.notification.application.port.out;

import de.ibikus.appa.hephaestus.eventskafka.notification.domain.Notification;
import java.util.List;
import java.util.UUID;

public interface NotificationStore {

    /**
     * Records the event as processed and stores the notification, atomically. Returns false (and stores nothing)
     * if the event id was processed before: that is the idempotency of the consumer.
     */
    boolean saveOnce(UUID eventId, Notification notification);

    List<Notification> findByCustomer(String customerId);
}

package de.ibikus.appa.hephaestus.eventskafka.notification.application.port.in;

import de.ibikus.appa.hephaestus.eventskafka.notification.domain.OrderPlaced;

public interface HandleOrderPlaced {

    enum Outcome {
        NOTIFIED,
        DUPLICATE
    }

    /**
     * Notifies the customer once per event id: a redelivered or re-sent event is recognised and skipped.
     *
     * @throws de.ibikus.appa.hephaestus.eventskafka.notification.domain.NotificationFailedException sending failed (retry)
     */
    Outcome handle(OrderPlaced event);
}

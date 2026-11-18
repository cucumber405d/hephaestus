package de.ibikus.appa.hephaestus.eventskafka.notification.domain;

/** Sending failed (here: an injected fault); worth retrying, then the DLT. */
public class NotificationFailedException extends RuntimeException {

    public NotificationFailedException(String message) {
        super(message);
    }
}

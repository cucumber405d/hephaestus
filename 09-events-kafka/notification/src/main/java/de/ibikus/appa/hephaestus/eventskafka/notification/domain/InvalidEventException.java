package de.ibikus.appa.hephaestus.eventskafka.notification.domain;

/** The event cannot be understood (bad JSON, missing fields, unknown type): never retried, goes to the DLT. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

package de.ibikus.appa.hephaestus.servicecommunication.ordering.domain;

/** Base of all business errors of the Ordering context. */
public abstract class OrderingException extends RuntimeException {

    protected OrderingException(String message) {
        super(message);
    }

    protected OrderingException(String message, Throwable cause) {
        super(message, cause);
    }
}

package de.ibikus.appa.hephaestus.template.greeting.domain;

/** Violated recipient invariant. Domain exception: the adapter decides how to expose it. */
public class InvalidRecipientException extends RuntimeException {

    public InvalidRecipientException(String message) {
        super(message);
    }
}

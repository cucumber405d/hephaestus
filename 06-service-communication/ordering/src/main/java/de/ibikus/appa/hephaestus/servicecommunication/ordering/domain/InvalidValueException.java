package de.ibikus.appa.hephaestus.servicecommunication.ordering.domain;

/** A value violates a domain invariant (maps to 400). */
public class InvalidValueException extends OrderingException {

    public InvalidValueException(String message) {
        super(message);
    }
}

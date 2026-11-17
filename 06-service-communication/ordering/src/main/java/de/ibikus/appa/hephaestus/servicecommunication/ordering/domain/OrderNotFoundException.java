package de.ibikus.appa.hephaestus.servicecommunication.ordering.domain;

/** No order with the given id (maps to 404). */
public class OrderNotFoundException extends OrderingException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}

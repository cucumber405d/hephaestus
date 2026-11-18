package de.ibikus.appa.hephaestus.observability.ordering.orders;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("Order " + orderId + " not found");
    }
}

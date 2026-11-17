package de.ibikus.appa.hephaestus.security.ordering.domain;

import java.util.Objects;

/** Who asks for an order: customers see their own orders, admins see all. */
public record Requester(String subject, boolean admin) {

    public Requester {
        Objects.requireNonNull(subject, "subject");
    }

    public boolean mayRead(Order order) {
        return admin || order.customerId().equals(subject);
    }
}

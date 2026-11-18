package de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.out;

import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;

/** Tells the rest of the world what happened to orders. */
public interface OrderEvents {

    /**
     * Publishes OrderPlaced and waits until the broker has it.
     *
     * @throws de.ibikus.appa.hephaestus.eventskafka.ordering.domain.EventPublishException broker did not confirm
     */
    void orderPlaced(Order order);
}

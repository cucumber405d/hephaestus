package de.ibikus.appa.hephaestus.monolith.forge.application;

import java.util.List;

/**
 * Port: the monolith's open work orders (Strangler Fig, variant A: they finish over there). The new system wraps the
 * old one for the smith's list; the adapter is an anti-corruption layer towards the monolith.
 */
public interface MonolithWorkOrders {

    /** False when no monolith is configured (before the transition or after decommission). */
    boolean configured();

    /**
     * @throws MonolithUnavailable when the monolith does not answer in time
     */
    List<ListedWorkOrder> open();

    class MonolithUnavailable extends RuntimeException {

        public MonolithUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

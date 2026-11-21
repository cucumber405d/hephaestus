package de.ibikus.appa.hephaestus.monolith.modular.migration;

/**
 * Where new work orders go during the Strangler Fig (ADR-0009). Ordering asks inside its own transaction, when it
 * publishes the request: the answer is then fixed in the event's type and survives any redelivery.
 */
public interface ForgeRouting {

    boolean remote();
}

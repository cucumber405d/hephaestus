package de.ibikus.appa.hephaestus.es.forge.application;

/** Where outcomes go (es.forge.events). Returns only after the broker acknowledged; throws when it did not. */
public interface OutcomeSink {

    void send(Outcome outcome);
}

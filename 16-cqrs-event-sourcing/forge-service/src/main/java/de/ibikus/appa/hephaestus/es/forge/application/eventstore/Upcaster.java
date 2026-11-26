package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

import tools.jackson.databind.JsonNode;

/** Raises one event type from {@link #fromVersion()} to the next schema version, on read; writes are always current. */
public interface Upcaster {

    String type();

    int fromVersion();

    JsonNode up(JsonNode payload);
}

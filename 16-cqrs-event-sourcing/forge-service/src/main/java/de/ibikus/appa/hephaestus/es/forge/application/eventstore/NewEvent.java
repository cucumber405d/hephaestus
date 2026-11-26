package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

import java.util.Map;

/** An event to append: {@code payload} is serialized as JSON; {@code metadata} holds correlation and causation ids. */
public record NewEvent(String type, int schemaVersion, Object payload, Map<String, String> metadata) {}

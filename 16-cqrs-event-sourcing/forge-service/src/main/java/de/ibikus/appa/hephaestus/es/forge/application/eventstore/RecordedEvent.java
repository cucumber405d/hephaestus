package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** A journal row: its global position, the writing transaction and its place in the stream. */
public record RecordedEvent(
        long position,
        String txId,
        String streamId,
        String streamType,
        int version,
        String type,
        int schemaVersion,
        JsonNode payload,
        Map<String, String> metadata,
        Instant recordedAt) {}

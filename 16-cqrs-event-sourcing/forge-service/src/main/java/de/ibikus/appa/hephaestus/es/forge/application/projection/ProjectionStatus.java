package de.ibikus.appa.hephaestus.es.forge.application.projection;

/** {@code lag}: journal events the projection has not applied yet. */
public record ProjectionStatus(String name, boolean enabled, String txId, long position, long lag, String lastError) {}

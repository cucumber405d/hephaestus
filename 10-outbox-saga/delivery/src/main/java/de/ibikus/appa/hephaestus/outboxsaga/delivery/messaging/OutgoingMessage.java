package de.ibikus.appa.hephaestus.outboxsaga.delivery.messaging;

/**
 * A message on its way out: published as a Spring application event inside the business transaction, stored by
 * Spring Modulith's event publication registry in the same transaction (the outbox), sent to {@code topic} after
 * the commit.
 */
public record OutgoingMessage(String topic, SagaMessage message) {}

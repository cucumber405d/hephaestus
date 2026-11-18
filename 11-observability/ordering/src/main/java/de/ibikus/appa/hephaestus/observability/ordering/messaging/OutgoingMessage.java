package de.ibikus.appa.hephaestus.observability.ordering.messaging;

/**
 * A message on its way out: published as a Spring application event inside the business transaction, stored by
 * Spring Modulith's event publication registry in the same transaction (the outbox), sent to {@code topic} after
 * the commit by {@link OutboxRelay}. {@code traceparent}: the trace the message was written in, so the send continues
 * it (null when carrying the trace is switched off).
 */
public record OutgoingMessage(String topic, SagaMessage message, String traceparent) {}

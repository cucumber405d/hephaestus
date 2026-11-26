package de.ibikus.appa.hephaestus.es.ordering.ordering.internal;

/** Port: Forge's quote for a custom piece. Never throws — a refusal carries the reason the customer sees. */
public interface QuoteClient {

    QuoteResult quote(String material, int quantity);

    sealed interface QuoteResult {}

    record Quoted(String quoteId, long priceCents, int leadTimeDays) implements QuoteResult {}

    record Refused(String reason) implements QuoteResult {}
}

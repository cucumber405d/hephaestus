package de.ibikus.appa.hephaestus.cdc.forge.application;

/** The quote a work order request refers to: which one, and the price Ordering accepted (-1 when it sent none). */
public record QuoteRef(String quoteId, long priceCents) {}

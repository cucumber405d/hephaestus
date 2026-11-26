package de.ibikus.appa.hephaestus.es.forge.application;

import java.util.OptionalInt;

/** The query side of the stock-ledger projection. */
public interface StockLedger {

    OptionalInt onHand(String material);
}

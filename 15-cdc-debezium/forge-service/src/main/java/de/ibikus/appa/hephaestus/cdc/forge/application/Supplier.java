package de.ibikus.appa.hephaestus.cdc.forge.application;

import de.ibikus.appa.hephaestus.cdc.forge.domain.SupplyResult;

/** Port: the material supplier, in Forge's words (the adapter is the anti-corruption layer). */
public interface Supplier {

    SupplyResult order(String material, int quantity);
}

package de.ibikus.appa.hephaestus.monolith.modular.supplier;

/**
 * The anti-corruption layer towards the material supplier: Forge asks in its own words and gets an answer in its
 * own words. The supplier's XML, field names and numeric codes stay in {@code supplier.internal}.
 */
public interface SupplierGateway {

    SupplyResult order(String material, int quantity);
}

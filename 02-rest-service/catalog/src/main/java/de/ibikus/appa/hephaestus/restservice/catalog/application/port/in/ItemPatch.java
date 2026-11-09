package de.ibikus.appa.hephaestus.restservice.catalog.application.port.in;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;

/**
 * A parsed JSON Merge Patch for an item. The price is patched field by field (amount, currency) and merged with
 * the current price by the service.
 */
public record ItemPatch(
        PatchValue<String> name,
        PatchValue<String> description,
        PatchValue<String> priceAmount,
        PatchValue<String> priceCurrency,
        PatchValue<CategoryId> categoryId) {}

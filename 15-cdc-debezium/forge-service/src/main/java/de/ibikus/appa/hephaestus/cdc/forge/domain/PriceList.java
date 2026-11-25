package de.ibikus.appa.hephaestus.cdc.forge.domain;

import java.util.Map;

/**
 * Forge's fixed price list: price = rate × quantity, lead time = base days + ceil(quantity / units per day). The
 * supplier is not asked (quotes stay fast); a shortfall is Forge's risk, as with a real workshop's catalogue.
 */
public final class PriceList {

    /** Cents per unit and the days the material needs before any work. */
    public record Rate(long centsPerUnit, int baseDays) {}

    public record Price(long priceCents, int leadTimeDays) {}

    private final Map<String, Rate> rates;
    private final int unitsPerDay;

    public PriceList(Map<String, Rate> rates, int unitsPerDay) {
        if (unitsPerDay < 1) {
            throw new IllegalArgumentException("units per day must be positive: " + unitsPerDay);
        }
        this.rates = Map.copyOf(rates);
        this.unitsPerDay = unitsPerDay;
    }

    /**
     * @throws UnknownMaterial when the material is not on the list
     * @throws InvalidQuantity when {@code quantity} is below 1
     */
    public Price price(String material, int quantity) {
        var rate = rates.get(material);
        if (rate == null) {
            throw new UnknownMaterial(material);
        }
        if (quantity < 1) {
            throw new InvalidQuantity(quantity);
        }
        var days = rate.baseDays() + (quantity + unitsPerDay - 1) / unitsPerDay;
        return new Price(Math.multiplyExact(rate.centsPerUnit(), quantity), days);
    }
}

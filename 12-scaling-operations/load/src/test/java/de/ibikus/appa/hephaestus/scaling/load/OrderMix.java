package de.ibikus.appa.hephaestus.scaling.load;

/**
 * Who buys what in the peak sale, by the run and the order's sequence number: 8 of 10 orders succeed (a fresh customer, a cheap
 * item within the default credit limit of 500 EUR), 1 is declined by Payment (bob, limit 50 EUR), 1 by Inventory
 * (TONGS-01, never in stock). Each kind runs in both saga styles. Deterministic, so the expectation is known.
 */
public final class OrderMix {

    public static final String CONFIRMED = "CONFIRMED";
    public static final String CREDIT_LIMIT = "REJECTED:credit limit exceeded";
    public static final String NO_STOCK = "REJECTED:not enough stock";

    /** One order to place, with the outcome the saga must reach for it. */
    public record Order(String customer, String style, String sku, String expected) {}

    private OrderMix() {}

    /** The run id keeps customers fresh across runs: confirmed orders keep their credit reserved. */
    public static Order of(String run, long i) {
        var style = (i / 10) % 2 == 0 ? "choreography" : "orchestration";
        return switch ((int) (i % 10)) {
            case 8 -> new Order("bob", style, "ANVIL-01", CREDIT_LIMIT);
            case 9 -> new Order("peak-" + run + "-" + i, style, "TONGS-01", NO_STOCK);
            default -> new Order("peak-" + run + "-" + i, style, i % 2 == 0 ? "HAMMER-01" : "ANVIL-01", CONFIRMED);
        };
    }
}

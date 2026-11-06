package de.ibikus.appa.hephaestus.modernjava.java12;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/** Java 12 {@code Collectors.teeing} and {@code String.transform}. */
public final class OrderStatistics {

    public record PriceSummary(BigDecimal total, BigDecimal average) {}

    private OrderStatistics() {}

    /** Total and average in a single pass: teeing feeds every element to two collectors and merges results. */
    public static PriceSummary summarize(List<BigDecimal> prices) {
        return prices.stream()
                .collect(Collectors.teeing(
                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add),
                        Collectors.counting(),
                        (total, count) -> new PriceSummary(
                                total,
                                count == 0
                                        ? BigDecimal.ZERO
                                        : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP))));
    }

    public static String label(String orderId) {
        return orderId.transform(String::strip).transform(String::toUpperCase).transform(id -> "ORDER " + id);
    }
}

package de.ibikus.appa.hephaestus.modernjava.java16;

import java.util.List;

/** Java 16: records, pattern matching for {@code instanceof} (JEP 394), {@code Stream.toList} and {@code mapMulti}. */
public record OrderLine(String materialCode, int quantity, Money unitPrice) {

    public OrderLine {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
    }

    public Money total() {
        return unitPrice.times(quantity);
    }

    /** One material code per unit; {@code mapMulti} emits zero or more elements without creating inner streams. */
    public static List<String> expandUnits(List<OrderLine> lines) {
        return lines.stream()
                .<String>mapMulti((line, downstream) -> {
                    for (int i = 0; i < line.quantity(); i++) {
                        downstream.accept(line.materialCode());
                    }
                })
                .toList();
    }

    /** The binding variable is only in scope where the pattern matched: no cast, no second lookup. */
    public static String describe(Object value) {
        if (value instanceof OrderLine line) {
            return line.quantity() + " x " + line.materialCode();
        }
        if (value instanceof Money money && money.amount().signum() >= 0) {
            return money.toString();
        }
        return "unknown";
    }
}

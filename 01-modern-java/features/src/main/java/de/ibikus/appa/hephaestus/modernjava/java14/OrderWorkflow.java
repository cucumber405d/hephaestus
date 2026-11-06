package de.ibikus.appa.hephaestus.modernjava.java14;

/**
 * Java 14 switch expressions (JEP 361; preview in 12 and 13): arrow labels, no fall-through, the switch yields a
 * value and must be exhaustive over the enum.
 */
public final class OrderWorkflow {

    private OrderWorkflow() {}

    /** A block on the right of {@code ->} returns its value with {@code yield}. */
    public static String label(OrderStatus status) {
        return switch (status) {
            case IN_FORGE -> {
                String base = status.name().toLowerCase().replace('_', ' ');
                yield base + " (being made)";
            }
            default -> status.name().toLowerCase();
        };
    }

    public static OrderStatus next(OrderStatus current) {
        return switch (current) {
            case NEW -> OrderStatus.PAID;
            case PAID -> OrderStatus.IN_FORGE;
            case IN_FORGE -> OrderStatus.SHIPPED;
            case SHIPPED, CANCELLED -> {
                String reason = "Order is already " + current.name().toLowerCase();
                throw new IllegalStateException(reason);
            }
        };
    }
}

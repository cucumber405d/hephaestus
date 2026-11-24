package de.ibikus.appa.hephaestus.monolith.modular.supplier;

/** What Forge needs to know about a supply attempt — nothing about codes 0/17/42. */
public sealed interface SupplyResult {

    /** The supplier delivered {@code quantity}. */
    record Delivered(int quantity) implements SupplyResult {}

    /** The supplier will never deliver this: retrying makes no sense. */
    record PermanentFailure(String reason) implements SupplyResult {

        public static final String NOT_DELIVERABLE = "not deliverable";
        public static final String UNKNOWN_ARTICLE = "unknown article";
    }

    /** The supplier could not be reached or answered nonsense: try again later. */
    record TransientFailure(String reason) implements SupplyResult {}
}

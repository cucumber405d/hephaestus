package de.ibikus.appa.hephaestus.contracts.forge.domain;

/** What Forge needs to know about a supply attempt (copied from the monolith's ACL, not shared: root rule). */
public sealed interface SupplyResult {

    record Delivered(int quantity) implements SupplyResult {}

    record PermanentFailure(String reason) implements SupplyResult {

        public static final String NOT_DELIVERABLE = "not deliverable";
        public static final String UNKNOWN_ARTICLE = "unknown article";
    }

    record TransientFailure(String reason) implements SupplyResult {}
}

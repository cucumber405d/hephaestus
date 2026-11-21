package de.ibikus.appa.hephaestus.monolith.legacy.forge;

import java.util.UUID;

/** {@code lieferstatus}: the supplier's numeric code, stored as is (tangle 3). */
public record WorkOrder(
        String id, UUID orderId, String material, int quantity, String status, Integer lieferstatus, String origin) {}

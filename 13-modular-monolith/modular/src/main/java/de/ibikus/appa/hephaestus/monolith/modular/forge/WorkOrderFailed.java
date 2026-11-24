package de.ibikus.appa.hephaestus.monolith.modular.forge;

import java.util.UUID;

public record WorkOrderFailed(UUID orderId, String workOrderId, String reason) {}

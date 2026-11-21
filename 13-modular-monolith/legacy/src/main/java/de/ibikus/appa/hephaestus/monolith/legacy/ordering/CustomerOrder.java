package de.ibikus.appa.hephaestus.monolith.legacy.ordering;

import java.util.UUID;

public record CustomerOrder(UUID id, String status, String reason) {}

package de.ibikus.appa.hephaestus.monolith.modular.ordering;

import java.util.UUID;

public record CustomerOrder(UUID id, String status, String reason) {}

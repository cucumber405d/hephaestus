package de.ibikus.appa.hephaestus.monolith.forge.application;

import java.util.UUID;

/** A line of the smith's list: the service's own work orders and, during the transition, the monolith's. */
public record ListedWorkOrder(String id, UUID orderId, String material, int quantity, String status, String origin) {}

package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

record ItemResponse(UUID id, String name, String description, PriceJson price, UUID categoryId, Instant createdAt) {}

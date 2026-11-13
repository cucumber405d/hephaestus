package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import java.util.UUID;

/** Body of POST /api/items; validation happens in the domain (NewItem, Money). */
record ItemRequest(String name, String description, PriceJson price, UUID categoryId) {}

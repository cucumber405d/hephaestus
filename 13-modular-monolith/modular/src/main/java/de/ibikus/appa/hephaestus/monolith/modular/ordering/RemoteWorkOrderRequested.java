package de.ibikus.appa.hephaestus.monolith.modular.ordering;

import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * The request for forge-service, after the cutover. A type of its own, decided at publication: the local forge never
 * listens to it, Spring Modulith externalizes it to Kafka (after commit, tracked in the publication registry), key =
 * the customer order. JSON: {@code {"orderId", "material", "quantity"}}.
 */
@Externalized("forge.requests::#{#this.orderId()}")
public record RemoteWorkOrderRequested(UUID orderId, String material, int quantity) {}

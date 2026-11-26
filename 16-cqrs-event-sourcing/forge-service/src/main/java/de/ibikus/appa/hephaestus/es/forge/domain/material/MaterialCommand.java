package de.ibikus.appa.hephaestus.es.forge.domain.material;

import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived.Source;

public sealed interface MaterialCommand {

    record Receive(int quantity, Source source) implements MaterialCommand {}

    record Take(int quantity, String workOrderId) implements MaterialCommand {}
}

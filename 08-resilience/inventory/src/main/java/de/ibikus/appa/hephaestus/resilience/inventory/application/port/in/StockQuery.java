package de.ibikus.appa.hephaestus.resilience.inventory.application.port.in;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.StockItem;

public interface StockQuery {

    StockItem get(String sku);
}

package de.ibikus.appa.hephaestus.security.inventory.application.port.in;

import de.ibikus.appa.hephaestus.security.inventory.domain.StockItem;

public interface StockQuery {

    StockItem get(String sku);
}

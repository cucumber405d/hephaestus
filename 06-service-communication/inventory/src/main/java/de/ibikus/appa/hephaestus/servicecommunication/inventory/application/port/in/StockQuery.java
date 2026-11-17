package de.ibikus.appa.hephaestus.servicecommunication.inventory.application.port.in;

import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.StockItem;

public interface StockQuery {

    StockItem get(String sku);
}

package de.ibikus.appa.hephaestus.servicecommunication.inventory.application.service;

import de.ibikus.appa.hephaestus.servicecommunication.inventory.application.port.in.StockQuery;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.application.port.out.StockRepository;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.StockItem;
import de.ibikus.appa.hephaestus.servicecommunication.inventory.domain.StockItemNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService implements StockQuery {

    private final StockRepository stock;

    public StockService(StockRepository stock) {
        this.stock = stock;
    }

    @Override
    @Transactional(readOnly = true)
    public StockItem get(String sku) {
        return stock.findBySku(sku).orElseThrow(() -> new StockItemNotFoundException(sku));
    }
}

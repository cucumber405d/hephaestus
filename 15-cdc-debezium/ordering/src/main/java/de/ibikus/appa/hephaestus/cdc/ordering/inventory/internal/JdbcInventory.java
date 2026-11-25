package de.ibikus.appa.hephaestus.cdc.ordering.inventory.internal;

import de.ibikus.appa.hephaestus.cdc.ordering.inventory.Inventory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcInventory implements Inventory {

    private final JdbcClient jdbc;

    JdbcInventory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean reserve(String sku, int quantity) {
        return jdbc.sql("""
                        update inventory.stock_item set reserved = reserved + ?
                        where sku = ? and on_hand - reserved >= ?""").params(quantity, sku, quantity).update() == 1;
    }
}

package de.ibikus.appa.hephaestus.monolith.legacy.inventory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Finished goods on the shelf. Forge bypasses this class and writes stock_item itself (tangle 2). */
@Component
public class Inventory {

    private final JdbcClient jdbc;

    Inventory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean reserve(String sku, int quantity) {
        return jdbc.sql("update stock_item set reserved = reserved + ? where sku = ? and on_hand - reserved >= ?")
                        .params(quantity, sku, quantity)
                        .update()
                == 1;
    }
}

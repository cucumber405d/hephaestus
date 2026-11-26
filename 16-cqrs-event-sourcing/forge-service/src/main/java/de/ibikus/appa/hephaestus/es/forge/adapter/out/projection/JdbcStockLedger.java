package de.ibikus.appa.hephaestus.es.forge.adapter.out.projection;

import de.ibikus.appa.hephaestus.es.forge.application.StockLedger;
import java.util.OptionalInt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStockLedger implements StockLedger {

    private final JdbcClient jdbc;

    JdbcStockLedger(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OptionalInt onHand(String material) {
        return jdbc.sql("select on_hand from read.stock_ledger where material = ?")
                .param(material)
                .query(Integer.class)
                .optional()
                .map(OptionalInt::of)
                .orElse(OptionalInt.empty());
    }
}

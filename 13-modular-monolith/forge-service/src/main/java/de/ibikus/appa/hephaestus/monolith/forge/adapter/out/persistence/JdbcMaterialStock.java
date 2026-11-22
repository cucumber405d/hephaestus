package de.ibikus.appa.hephaestus.monolith.forge.adapter.out.persistence;

import de.ibikus.appa.hephaestus.monolith.forge.application.MaterialStock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMaterialStock implements MaterialStock {

    private final JdbcClient jdbc;

    JdbcMaterialStock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int lockOnHand(String code) {
        return jdbc.sql("select on_hand from material where code = ? for update")
                .param(code)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }

    @Override
    public void add(String code, int quantity) {
        jdbc.sql("""
                        insert into material (code, on_hand) values (?, ?)
                        on conflict (code) do update set on_hand = material.on_hand + excluded.on_hand""").params(code, quantity).update();
    }

    @Override
    public void take(String code, int quantity) {
        jdbc.sql("update material set on_hand = on_hand - ? where code = ?")
                .params(quantity, code)
                .update();
    }
}

package de.ibikus.appa.hephaestus.monolith.modular.migration.internal;

import de.ibikus.appa.hephaestus.monolith.modular.migration.ForgeRouting;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcForgeRouting implements ForgeRouting {

    private final JdbcClient jdbc;

    JdbcForgeRouting(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean remote() {
        return jdbc.sql("select remote from migration.forge_cutover where id = 1")
                .query(Boolean.class)
                .single();
    }

    void remote(boolean remote) {
        jdbc.sql("update migration.forge_cutover set remote = ? where id = 1")
                .param(remote)
                .update();
    }
}

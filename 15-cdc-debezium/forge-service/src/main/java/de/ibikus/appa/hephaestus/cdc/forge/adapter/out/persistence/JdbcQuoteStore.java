package de.ibikus.appa.hephaestus.cdc.forge.adapter.out.persistence;

import de.ibikus.appa.hephaestus.cdc.forge.application.QuoteStore;
import de.ibikus.appa.hephaestus.cdc.forge.domain.Quote;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcQuoteStore implements QuoteStore {

    private final JdbcClient jdbc;

    JdbcQuoteStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Quote quote) {
        jdbc.sql("""
                        insert into quote (id, material, quantity, price_cents, lead_time_days, expires_at)
                        values (?, ?, ?, ?, ?, ?)""")
                .params(
                        quote.id(),
                        quote.material(),
                        quote.quantity(),
                        quote.priceCents(),
                        quote.leadTimeDays(),
                        Timestamp.from(quote.expiresAt()))
                .update();
    }

    @Override
    public Optional<Quote> find(String id) {
        return jdbc.sql(
                        "select id, material, quantity, price_cents, lead_time_days, expires_at from quote where id = ?")
                .param(id)
                .query((rs, row) -> new Quote(
                        rs.getString("id"),
                        rs.getString("material"),
                        rs.getInt("quantity"),
                        rs.getLong("price_cents"),
                        rs.getInt("lead_time_days"),
                        rs.getTimestamp("expires_at").toInstant()))
                .optional();
    }
}

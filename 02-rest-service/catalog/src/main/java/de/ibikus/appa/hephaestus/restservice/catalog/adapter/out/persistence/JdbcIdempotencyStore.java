package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyStore;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.StoredResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Idempotency keys in PostgreSQL. Plain JDBC on purpose: the reservation needs {@code INSERT ... ON CONFLICT DO
 * NOTHING}, which makes "claim the key" atomic across concurrent requests. Each statement auto-commits, so other
 * requests see a reservation immediately.
 */
@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbc;

    JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean reserve(String key, String requestHash, Instant now) {
        return jdbc.update(
                        "insert into idempotency_key (key, request_hash, response_status, created_at)"
                                + " values (?, ?, 0, ?) on conflict (key) do nothing",
                        key,
                        requestHash,
                        now.atOffset(ZoneOffset.UTC))
                == 1;
    }

    @Override
    public void complete(String key, StoredResponse response) {
        jdbc.update(
                "update idempotency_key set response_status = ?, response_body = ?::jsonb, location = ?, etag = ?"
                        + " where key = ?",
                response.status(),
                response.body(),
                response.location(),
                response.etag(),
                key);
    }

    @Override
    public void release(String key) {
        jdbc.update("delete from idempotency_key where key = ? and response_status = 0", key);
    }

    @Override
    public Optional<StoredResponse> find(String key) {
        return jdbc
                .query(
                        "select request_hash, response_status, response_body::text, location, etag"
                                + " from idempotency_key where key = ?",
                        (rs, row) -> new StoredResponse(
                                rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                        key)
                .stream()
                .findFirst();
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("delete from idempotency_key where created_at < ?", cutoff.atOffset(ZoneOffset.UTC));
    }
}

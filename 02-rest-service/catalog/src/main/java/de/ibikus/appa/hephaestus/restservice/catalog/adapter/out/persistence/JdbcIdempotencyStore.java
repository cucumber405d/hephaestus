package de.ibikus.appa.hephaestus.restservice.catalog.adapter.out.persistence;

import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyRecord;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.out.IdempotencyStore;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.IdempotencyConflictException;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Idempotency keys in PostgreSQL via JDBC. JdbcTemplate joins the JPA transaction (same connection), so the key and
 * the item commit or roll back together. A concurrent insert of the same key waits on the primary key until the
 * first transaction ends, then fails with a duplicate key.
 */
@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbc;

    JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        return jdbc
                .query(
                        "select key, request_hash, item_id, created_at from idempotency_key where key = ?",
                        (rs, row) -> new IdempotencyRecord(
                                rs.getString(1),
                                rs.getString(2),
                                new ItemId(rs.getObject(3, UUID.class)),
                                rs.getObject(4, java.time.OffsetDateTime.class).toInstant()),
                        key)
                .stream()
                .findFirst();
    }

    @Override
    public void insert(IdempotencyRecord record) {
        try {
            jdbc.update(
                    "insert into idempotency_key (key, request_hash, item_id, created_at) values (?, ?, ?, ?)",
                    record.key(),
                    record.requestHash(),
                    record.itemId().value(),
                    record.createdAt().atOffset(ZoneOffset.UTC));
        } catch (DuplicateKeyException e) {
            throw new IdempotencyConflictException(
                    "A request with this Idempotency-Key was processed concurrently; retry to get its result");
        }
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("delete from idempotency_key where created_at < ?", cutoff.atOffset(ZoneOffset.UTC));
    }
}

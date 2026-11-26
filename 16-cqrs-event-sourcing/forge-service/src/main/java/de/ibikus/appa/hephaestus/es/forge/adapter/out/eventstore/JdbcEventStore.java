package de.ibikus.appa.hephaestus.es.forge.adapter.out.eventstore;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.Checkpoint;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.ConcurrencyConflict;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventStore;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.NewEvent;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.RecordedEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The journal in {@code forge.event} (V3__event_store.sql). */
@Repository
public class JdbcEventStore implements EventStore {

    private static final String COLUMNS = "global_position, tx_id::text as tx_id, stream_id, stream_type, version, type,"
            + " schema_version, payload::text as payload, metadata::text as metadata, recorded_at";
    private static final TypeReference<Map<String, String>> METADATA = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public JdbcEventStore(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void append(String streamId, String streamType, int expectedVersion, List<NewEvent> events) {
        int current = jdbc.sql("select coalesce(max(version), 0) from forge.event where stream_id = ?")
                .param(streamId)
                .query(Integer.class)
                .single();
        if (current != expectedVersion) {
            throw new ConcurrencyConflict(streamId, expectedVersion);
        }
        int version = expectedVersion;
        try {
            for (var event : events) {
                jdbc.sql("""
                                insert into forge.event
                                    (stream_id, stream_type, version, type, schema_version, payload, metadata)
                                values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)""")
                        .params(
                                streamId,
                                streamType,
                                ++version,
                                event.type(),
                                event.schemaVersion(),
                                json.writeValueAsString(event.payload()),
                                json.writeValueAsString(event.metadata()))
                        .update();
            }
        } catch (DuplicateKeyException e) {
            // a concurrent writer took the version between the check and the insert
            throw new ConcurrencyConflict(streamId, expectedVersion);
        }
    }

    @Override
    public List<RecordedEvent> readStream(String streamId) {
        return jdbc.sql("select " + COLUMNS + " from forge.event where stream_id = ? order by version")
                .param(streamId)
                .query(this::map)
                .list();
    }

    @Override
    public List<RecordedEvent> readAll(Checkpoint after, int limit) {
        return jdbc.sql("select " + COLUMNS + """
                         from forge.event
                        where (tx_id, global_position) > (?::xid8, ?)
                          and tx_id < pg_snapshot_xmin(pg_current_snapshot())
                        order by tx_id, global_position
                        limit ?""")
                .params(after.txId(), after.position(), limit)
                .query(this::map)
                .list();
    }

    private RecordedEvent map(ResultSet rs, int row) throws SQLException {
        return new RecordedEvent(
                rs.getLong("global_position"),
                rs.getString("tx_id"),
                rs.getString("stream_id"),
                rs.getString("stream_type"),
                rs.getInt("version"),
                rs.getString("type"),
                rs.getInt("schema_version"),
                json.readTree(rs.getString("payload")),
                json.readValue(rs.getString("metadata"), METADATA),
                rs.getTimestamp("recorded_at").toInstant());
    }
}

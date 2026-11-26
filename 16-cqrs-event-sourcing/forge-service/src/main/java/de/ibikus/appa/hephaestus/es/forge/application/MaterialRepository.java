package de.ibikus.appa.hephaestus.es.forge.application;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventCodec;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventStore;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialDecider;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** A material = its stream {@code material-<code>}; also folds the stream up to a moment (time travel). */
@Component
public class MaterialRepository {

    static final String STREAM_TYPE = "Material";

    private final EventStore store;
    private final EventCodec codec;

    public MaterialRepository(EventStore store, EventCodec codec) {
        this.store = store;
        this.codec = codec;
    }

    public record Loaded(String code, MaterialState state, int version) {}

    public Loaded load(String code) {
        var rows = store.readStream(MaterialDecider.streamId(code));
        var events = rows.stream().map(row -> (MaterialEvent) codec.decode(row)).toList();
        return new Loaded(code, MaterialDecider.fold(events), rows.size());
    }

    /** The stock as the journal said at {@code at} (events recorded at or before it). */
    public int onHandAt(String code, Instant at) {
        var events = store.readStream(MaterialDecider.streamId(code)).stream()
                .filter(row -> !row.recordedAt().isAfter(at))
                .map(row -> (MaterialEvent) codec.decode(row))
                .toList();
        return MaterialDecider.fold(events).onHand();
    }

    /** Appends and returns the material as it is afterwards (several appends in one transaction chain on it). */
    public Loaded save(Loaded loaded, List<MaterialEvent> events, Map<String, String> metadata) {
        store.append(
                MaterialDecider.streamId(loaded.code()),
                STREAM_TYPE,
                loaded.version(),
                events.stream().map(e -> codec.encode(e, metadata)).toList());
        var state = loaded.state();
        for (var event : events) {
            state = MaterialDecider.evolve(state, event);
        }
        return new Loaded(loaded.code(), state, loaded.version() + events.size());
    }
}

package de.ibikus.appa.hephaestus.es.forge.application;

import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventCodec;
import de.ibikus.appa.hephaestus.es.forge.application.eventstore.EventStore;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderDecider;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderState;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** A work order = its stream: load folds the events, save appends against the version that was loaded. */
@Component
public class WorkOrderRepository {

    static final String STREAM_TYPE = "WorkOrder";

    private final EventStore store;
    private final EventCodec codec;

    public WorkOrderRepository(EventStore store, EventCodec codec) {
        this.store = store;
        this.codec = codec;
    }

    public record Loaded(WorkOrderState state, int version) {}

    public Loaded load(String id) {
        var rows = store.readStream(id);
        var events = rows.stream().map(row -> (WorkOrderEvent) codec.decode(row)).toList();
        return new Loaded(WorkOrderDecider.fold(events), rows.size());
    }

    public void save(String id, Loaded loaded, List<WorkOrderEvent> events, Map<String, String> metadata) {
        if (!events.isEmpty()) {
            store.append(
                    id,
                    STREAM_TYPE,
                    loaded.version(),
                    events.stream().map(e -> codec.encode(e, metadata)).toList());
        }
    }
}

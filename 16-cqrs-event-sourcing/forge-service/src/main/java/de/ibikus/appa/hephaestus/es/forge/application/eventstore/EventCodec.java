package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Domain events ↔ journal rows: the type is the record's simple name, the payload its JSON. Reading runs the upcasters
 * from the row's schema version to the current one; a type or version this build does not know is an error.
 */
@Component
public class EventCodec {

    /** Event types whose current schema version is above 1. */
    private static final Map<String, Integer> CURRENT_VERSIONS = Map.of("WorkOrderStarted", 2);

    private final JsonMapper json;
    private final Map<String, Class<?>> types;
    private final Map<String, Upcaster> upcasters;

    public EventCodec(JsonMapper json, List<Upcaster> upcasters) {
        // events without fields (WorkOrderCompleted) are valid payloads: {}
        this.json = json.rebuild().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).build();
        this.types = Stream.of(WorkOrderEvent.class, MaterialEvent.class)
                .flatMap(root -> Stream.of(root.getPermittedSubclasses()))
                .collect(Collectors.toMap(Class::getSimpleName, type -> type));
        this.upcasters = new HashMap<>();
        upcasters.forEach(u -> this.upcasters.put(u.type() + "@" + u.fromVersion(), u));
    }

    public NewEvent encode(Object event, Map<String, String> metadata) {
        var type = event.getClass().getSimpleName();
        if (!types.containsKey(type)) {
            throw new UnknownEvent("not a journal event: " + event.getClass().getName());
        }
        return new NewEvent(type, currentVersion(type), json.valueToTree(event), metadata);
    }

    public Object decode(RecordedEvent row) {
        var type = types.get(row.type());
        if (type == null) {
            throw new UnknownEvent("unknown event type " + row.type() + " at position " + row.position());
        }
        int current = currentVersion(row.type());
        if (row.schemaVersion() > current) {
            throw new UnknownEvent(row.type() + " schema version " + row.schemaVersion() + " is newer than " + current
                    + " (position " + row.position() + ")");
        }
        JsonNode payload = row.payload();
        for (int version = row.schemaVersion(); version < current; version++) {
            var upcaster = upcasters.get(row.type() + "@" + version);
            if (upcaster == null) {
                throw new UnknownEvent("no upcaster for " + row.type() + " v" + version);
            }
            payload = upcaster.up(payload);
        }
        return json.treeToValue(payload, type);
    }

    private static int currentVersion(String type) {
        return CURRENT_VERSIONS.getOrDefault(type, 1);
    }
}

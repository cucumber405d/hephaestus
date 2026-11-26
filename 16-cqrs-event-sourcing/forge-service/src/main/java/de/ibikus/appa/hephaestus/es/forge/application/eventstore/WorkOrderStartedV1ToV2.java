package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** WorkOrderStarted v1 named no smith; v2 does. Old events read as started by "unknown". */
@Component
public class WorkOrderStartedV1ToV2 implements Upcaster {

    @Override
    public String type() {
        return "WorkOrderStarted";
    }

    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public JsonNode up(JsonNode payload) {
        var v2 = (ObjectNode) payload.deepCopy();
        v2.put("smith", "unknown");
        return v2;
    }
}

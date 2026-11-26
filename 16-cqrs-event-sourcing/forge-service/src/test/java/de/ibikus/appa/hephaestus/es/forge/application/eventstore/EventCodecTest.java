package de.ibikus.appa.hephaestus.es.forge.application.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived.Source;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockTaken;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.MaterialAllocated;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderFailed;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderRequested;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderStarted;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EventCodecTest {

    final JsonMapper json = JsonMapper.builder().build();
    final EventCodec codec = new EventCodec(json, List.of(new WorkOrderStartedV1ToV2()));

    @Test
    void roundTripsEveryEventType() {
        var events = List.<Object>of(
                new WorkOrderRequested("FW-1", UUID.randomUUID(), "IRON-01", 5),
                new WorkOrderFailed("unknown material: FOO"),
                new MaterialAllocated(5),
                new WorkOrderStarted("anna"),
                new WorkOrderCompleted(),
                new StockReceived(20, Source.INITIAL),
                new StockTaken(5, "FW-1"));
        for (var event : events) {
            var encoded = codec.encode(event, Map.of("correlationId", "c-1"));
            assertThat(encoded.type()).isEqualTo(event.getClass().getSimpleName());
            assertThat(codec.decode(recorded(encoded.type(), encoded.schemaVersion(), encoded)))
                    .isEqualTo(event);
        }
        assertThat(codec.encode(new WorkOrderStarted("anna"), Map.of()).schemaVersion())
                .isEqualTo(2);
    }

    @Test
    void startedV1IsReadAsV2WithUnknownSmith() {
        var v1 = new NewEvent("WorkOrderStarted", 1, json.createObjectNode(), Map.of());
        assertThat(codec.decode(recorded("WorkOrderStarted", 1, v1))).isEqualTo(new WorkOrderStarted("unknown"));
    }

    @Test
    void unknownTypeThrows() {
        var bogus = new NewEvent("Bogus", 1, json.createObjectNode(), Map.of());
        assertThatThrownBy(() -> codec.decode(recorded("Bogus", 1, bogus)))
                .isInstanceOf(UnknownEvent.class)
                .hasMessageContaining("Bogus");
    }

    @Test
    void newerSchemaVersionThanKnownThrows() {
        var future = new NewEvent("WorkOrderStarted", 3, json.createObjectNode(), Map.of());
        assertThatThrownBy(() -> codec.decode(recorded("WorkOrderStarted", 3, future)))
                .isInstanceOf(UnknownEvent.class)
                .hasMessageContaining("3");
    }

    private RecordedEvent recorded(String type, int schemaVersion, NewEvent event) {
        return new RecordedEvent(
                1,
                "1",
                "s-1",
                "WorkOrder",
                1,
                type,
                schemaVersion,
                json.valueToTree(event.payload()),
                event.metadata(),
                Instant.now());
    }
}

package de.ibikus.appa.hephaestus.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.contracts.events.ForgeEvent;
import de.ibikus.appa.hephaestus.contracts.events.ForgeEventType;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** JSON (as in 13) against Avro binary for the same event; the sizes go to docs/learning-notes.md. */
class MessageSizeTest {

    @Test
    void avroIsSmallerThanJson() throws Exception {
        var event = ForgeEvent.newBuilder()
                .setEventId("0b8f3c1e-7d2a-4c55-9e61-2f7a9d0c4b13")
                .setType(ForgeEventType.WORK_ORDER_FAILED)
                .setOrderId("6f1c2a8e-0000-4000-8000-000000000001")
                .setWorkOrderId("FW-3a9e2c47-1d8b-4f0a-b6c2-5e7d9f1a2b3c")
                .setReason("material not deliverable: MITHRIL-01")
                .build();
        var out = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(out, null);
        new SpecificDatumWriter<>(ForgeEvent.class).write(event, encoder);
        encoder.flush();
        var avro = out.size();

        // The 13 shape: the same fields as a JSON object
        var fields = new LinkedHashMap<String, Object>();
        fields.put("eventId", event.getEventId());
        fields.put("type", "WorkOrderFailed");
        fields.put("orderId", event.getOrderId());
        fields.put("workOrderId", event.getWorkOrderId());
        fields.put("reason", event.getReason());
        var json = JsonMapper.builder().build().writeValueAsBytes(fields).length;

        System.out.printf("ForgeEvent: JSON %d bytes, Avro %d bytes (plus the registry id header on the wire)%n", json, avro);
        assertThat(avro).isLessThan(json);
    }
}

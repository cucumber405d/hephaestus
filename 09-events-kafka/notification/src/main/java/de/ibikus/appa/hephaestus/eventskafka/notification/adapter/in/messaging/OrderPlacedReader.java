package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging;

import de.ibikus.appa.hephaestus.eventskafka.notification.domain.InvalidEventException;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.OrderPlaced;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a raw record (String value) into the domain event. Anything that does not fit (wrong type header, broken
 * JSON, missing fields) is an {@link InvalidEventException}: a poison message that no retry can fix.
 */
@Component
public class OrderPlacedReader {

    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public OrderPlaced read(ConsumerRecord<String, String> record) {
        var type = record.headers().lastHeader(Topics.EVENT_TYPE_HEADER);
        if (type == null || !"OrderPlaced".equals(new String(type.value(), StandardCharsets.UTF_8))) {
            throw new InvalidEventException("Not an OrderPlaced event at offset " + record.offset());
        }
        try {
            return json.readValue(record.value(), OrderPlaced.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new InvalidEventException("Unreadable OrderPlaced at offset " + record.offset(), e);
        }
    }
}

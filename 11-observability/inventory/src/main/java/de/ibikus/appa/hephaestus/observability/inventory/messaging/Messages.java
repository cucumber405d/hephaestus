package de.ibikus.appa.hephaestus.observability.inventory.messaging;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** JSON text on the wire (Kafka values are plain strings): written and read here, nowhere else. */
@Component
public class Messages {

    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public String write(SagaMessage message) {
        return json.writeValueAsString(message);
    }

    /** @throws IllegalArgumentException not a saga message */
    public SagaMessage read(String text) {
        try {
            var message = json.readValue(text, SagaMessage.class);
            if (message.messageId() == null || message.type() == null || message.orderId() == null) {
                throw new IllegalArgumentException("Saga message without messageId, type or orderId");
            }
            return message;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Not a saga message: " + e.getOriginalMessage(), e);
        }
    }
}

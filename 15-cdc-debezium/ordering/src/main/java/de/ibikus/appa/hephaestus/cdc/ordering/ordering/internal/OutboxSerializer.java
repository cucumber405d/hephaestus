package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import de.ibikus.appa.hephaestus.contracts.events.WorkOrderRequest;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Component;

/**
 * Serializes the request exactly as the relay's producer would: the same Apicurio serializer, configured from the
 * same producer configuration (explicit schema version = contract version, schema id in the payload). Debezium then
 * copies these bytes to the topic unchanged (ByteArrayConverter) — Forge cannot tell the two modes apart.
 */
@Component
@ConditionalOnProperty(name = "outbox.mode", havingValue = "debezium", matchIfMissing = true)
class OutboxSerializer implements DisposableBean {

    private final AvroKafkaSerializer<WorkOrderRequest> serializer = new AvroKafkaSerializer<>();

    OutboxSerializer(ProducerFactory<?, ?> producers) {
        serializer.configure(producers.getConfigurationProperties(), false);
    }

    byte[] serialize(WorkOrderRequest request) {
        try {
            return serializer.serialize(WorkOrderRequests.TOPIC, request);
        } catch (RuntimeException e) {
            throw new SchemaRegistryUnavailable(e);
        }
    }

    @Override
    public void destroy() {
        serializer.close();
    }
}

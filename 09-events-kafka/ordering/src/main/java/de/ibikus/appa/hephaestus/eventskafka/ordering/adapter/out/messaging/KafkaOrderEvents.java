package de.ibikus.appa.hephaestus.eventskafka.ordering.adapter.out.messaging;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.out.OrderEvents;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.EventPublishException;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes OrderPlaced keyed by the customer id: Kafka hashes the key to a partition, so all events of one customer
 * land in the same partition and are consumed in the order they were sent. The send is synchronous (acks=all,
 * idempotent producer): the caller learns whether the broker has the event.
 */
@Component
class KafkaOrderEvents implements OrderEvents {

    static final String TOPIC = KafkaTopicsConfiguration.ORDERS_EVENTS;
    static final String EVENT_TYPE_HEADER = "eventType";
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    private final KafkaTemplate<String, OrderPlacedEvent> kafka;

    KafkaOrderEvents(KafkaTemplate<String, OrderPlacedEvent> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void orderPlaced(Order order) {
        var record = new ProducerRecord<String, OrderPlacedEvent>(TOPIC, order.customerId(), OrderPlacedEvent.of(order));
        record.headers().add(EVENT_TYPE_HEADER, "OrderPlaced".getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrupted while publishing OrderPlaced " + order.eventId(), e);
        } catch (ExecutionException | TimeoutException | KafkaException e) {
            throw new EventPublishException("Could not publish OrderPlaced " + order.eventId(), e);
        }
    }
}

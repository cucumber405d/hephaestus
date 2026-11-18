package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging;

import de.ibikus.appa.hephaestus.eventskafka.notification.application.port.in.HandleOrderPlaced;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Classic consumer group "notification": each partition belongs to exactly one member at a time, so the order
 * within a partition (= per customer) is kept, and more members than partitions sit idle. Failures go to the
 * error handler ({@link ErrorHandlingConfiguration}): retries, then the dead letter topic.
 */
@Component
@ConditionalOnProperty(name = "notification.mode", havingValue = "classic", matchIfMissing = true)
class ClassicOrderEventsListener {

    static final String ID = "notification";

    private final OrderPlacedReader reader;
    private final HandleOrderPlaced handler;

    ClassicOrderEventsListener(OrderPlacedReader reader, HandleOrderPlaced handler) {
        this.reader = reader;
        this.handler = handler;
    }

    @KafkaListener(id = ID, groupId = "notification", topics = Topics.ORDERS_EVENTS)
    void onEvent(ConsumerRecord<String, String> record) {
        handler.handle(reader.read(record));
    }
}

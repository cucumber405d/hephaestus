package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging;

import de.ibikus.appa.hephaestus.eventskafka.notification.application.port.in.HandleOrderPlaced;
import de.ibikus.appa.hephaestus.eventskafka.notification.domain.InvalidEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Share group "notification-share": records of one partition are handed out to any free member and acknowledged
 * one by one, so more members than partitions all get work, but the per-customer order is no longer guaranteed.
 * A failed notification is released and redelivered (to any member) until the broker's delivery limit, then
 * archived: share groups have no dead letter topic (see the recoverer in {@link ShareGroupConfiguration}). An
 * invalid event is logged and acknowledged, since redelivery cannot fix it.
 */
@Component
@ConditionalOnProperty(name = "notification.mode", havingValue = "share")
class ShareOrderEventsListener {

    static final String ID = "notification-share";
    private static final Logger LOG = LoggerFactory.getLogger(ShareOrderEventsListener.class);

    private final OrderPlacedReader reader;
    private final HandleOrderPlaced handler;

    ShareOrderEventsListener(OrderPlacedReader reader, HandleOrderPlaced handler) {
        this.reader = reader;
        this.handler = handler;
    }

    @KafkaListener(
            id = ID,
            groupId = "notification-share",
            topics = Topics.ORDERS_EVENTS,
            containerFactory = "shareKafkaListenerContainerFactory")
    void onEvent(ConsumerRecord<String, String> record) {
        try {
            handler.handle(reader.read(record));
        } catch (InvalidEventException e) {
            LOG.warn("Dropping invalid event (share groups have no DLT): {}", e.getMessage());
        }
    }
}

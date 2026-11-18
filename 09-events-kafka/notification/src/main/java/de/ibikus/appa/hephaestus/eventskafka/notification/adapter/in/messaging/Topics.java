package de.ibikus.appa.hephaestus.eventskafka.notification.adapter.in.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics Notification relies on, created if missing (the same definition as Ordering's for orders.events, so
 * either service may start first). The dead letter topic belongs to the consumer: it holds what we could not handle.
 */
@Configuration(proxyBeanMethods = false)
public class Topics {

    public static final String ORDERS_EVENTS = "orders.events";
    public static final String ORDERS_EVENTS_DLT = "orders.events-dlt";
    public static final String EVENT_TYPE_HEADER = "eventType";

    @Bean
    NewTopic ordersEvents() {
        return TopicBuilder.name(ORDERS_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic ordersEventsDlt() {
        return TopicBuilder.name(ORDERS_EVENTS_DLT).partitions(1).replicas(1).build();
    }
}

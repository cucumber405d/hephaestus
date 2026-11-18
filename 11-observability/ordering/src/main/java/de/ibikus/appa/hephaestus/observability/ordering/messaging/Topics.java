package de.ibikus.appa.hephaestus.observability.ordering.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * All saga topics, declared by every service (creation is idempotent, any service may start first). Three
 * partitions each; the key is the order id, so all messages about one order are consumed in order.
 */
@Configuration(proxyBeanMethods = false)
public class Topics {

    public static final String ORDERING_EVENTS = "ordering.events";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String INVENTORY_EVENTS = "inventory.events";
    public static final String DELIVERY_EVENTS = "delivery.events";
    public static final String PAYMENT_COMMANDS = "payment.commands";
    public static final String INVENTORY_COMMANDS = "inventory.commands";
    public static final String DELIVERY_COMMANDS = "delivery.commands";
    public static final String ORDERING_REPLIES = "ordering.replies";

    @Bean
    NewTopic orderingEvents() {
        return topic(ORDERING_EVENTS);
    }

    @Bean
    NewTopic paymentEvents() {
        return topic(PAYMENT_EVENTS);
    }

    @Bean
    NewTopic inventoryEvents() {
        return topic(INVENTORY_EVENTS);
    }

    @Bean
    NewTopic deliveryEvents() {
        return topic(DELIVERY_EVENTS);
    }

    @Bean
    NewTopic paymentCommands() {
        return topic(PAYMENT_COMMANDS);
    }

    @Bean
    NewTopic inventoryCommands() {
        return topic(INVENTORY_COMMANDS);
    }

    @Bean
    NewTopic deliveryCommands() {
        return topic(DELIVERY_COMMANDS);
    }

    @Bean
    NewTopic orderingReplies() {
        return topic(ORDERING_REPLIES);
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}

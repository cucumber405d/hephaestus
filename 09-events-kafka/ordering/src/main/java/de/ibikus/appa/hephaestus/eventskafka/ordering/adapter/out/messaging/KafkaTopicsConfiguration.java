package de.ibikus.appa.hephaestus.eventskafka.ordering.adapter.out.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The topic Ordering owns, created at startup by Spring's KafkaAdmin if missing. Three partitions: up to three
 * consumers of one group can work in parallel. One replica: the local broker is single-node.
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopicsConfiguration {

    static final String ORDERS_EVENTS = "orders.events";

    @Bean
    NewTopic ordersEvents() {
        return TopicBuilder.name(ORDERS_EVENTS).partitions(3).replicas(1).build();
    }
}

package de.ibikus.appa.hephaestus.monolith.modular.migration.internal;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** The two topics between monolith and forge-service; key = the customer order id. */
@Configuration(proxyBeanMethods = false)
class ForgeTopics {

    static final String REQUESTS = "forge.requests";
    static final String EVENTS = "forge.events";

    @Bean
    NewTopic forgeRequests() {
        return TopicBuilder.name(REQUESTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic forgeEvents() {
        return TopicBuilder.name(EVENTS).partitions(3).replicas(1).build();
    }
}

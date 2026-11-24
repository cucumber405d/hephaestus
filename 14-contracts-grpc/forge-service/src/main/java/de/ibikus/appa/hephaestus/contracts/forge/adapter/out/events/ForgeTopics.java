package de.ibikus.appa.hephaestus.contracts.forge.adapter.out.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** The two topics between Ordering and forge-service (declared on both sides; whoever starts first creates them). */
@Configuration(proxyBeanMethods = false)
class ForgeTopics {

    @Bean
    NewTopic forgeRequests() {
        return TopicBuilder.name("forge.requests").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic forgeEvents() {
        return TopicBuilder.name("forge.events").partitions(3).replicas(1).build();
    }
}

package de.ibikus.appa.hephaestus.monolith.forge.adapter.in.kafka;

import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Spring Kafka's default retries ten times without a pause and then commits past the record: a database restart of a
 * few seconds would silently drop a request (review I3). Here: back off up to KAFKA_RETRY_MAX_ELAPSED (5 min), then
 * park the record on {@code <topic>.DLT}; poison (unreadable JSON, unknown type) goes there at once.
 */
@Configuration(proxyBeanMethods = false)
class KafkaErrorHandling {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            ProducerFactory<?, ?> producers, @Value("${kafka.retry.max-elapsed}") Duration maxElapsed) {
        // the record as it came (String): not through the JSON serializer Spring Modulith sets on the template
        var template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                producers.getConfigurationProperties(), new StringSerializer(), new StringSerializer()));
        // partition -1: let Kafka choose (an auto-created DLT has fewer partitions than the topic)
        var recoverer = new DeadLetterPublishingRecoverer(
                template, (record, e) -> new TopicPartition(record.topic() + ".DLT", -1));
        var backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxInterval(10_000);
        backOff.setMaxElapsedTime(maxElapsed.toMillis());
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}

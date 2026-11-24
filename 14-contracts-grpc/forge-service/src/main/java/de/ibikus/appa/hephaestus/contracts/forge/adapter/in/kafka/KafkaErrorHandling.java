package de.ibikus.appa.hephaestus.contracts.forge.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Spring Kafka's default retries ten times without a pause and then commits past the record: a database restart of a
 * few seconds would silently drop a request (review I3). Here: back off up to KAFKA_RETRY_MAX_ELAPSED (5 min), then
 * park the record on {@code <topic>.DLT}. Unreadable bytes (not Avro of a registered schema) go there at once, as
 * they came; a record that was read but failed (a malformed id: IllegalArgumentException, not retried) goes there as
 * the Avro record's JSON text — the DLT is for people, and it has no schema in the registry.
 */
@Configuration(proxyBeanMethods = false)
class KafkaErrorHandling {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            ProducerFactory<?, ?> producers, @Value("${kafka.retry.max-elapsed}") Duration maxElapsed) {
        // poison arrives as raw bytes (ErrorHandlingDeserializer), a failed record as its Avro class
        var values = new LinkedHashMap<Class<?>, Serializer<?>>();
        values.put(byte[].class, new ByteArraySerializer());
        values.put(SpecificRecord.class, avroAsJsonText());
        var template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                producers.getConfigurationProperties(),
                new StringSerializer(),
                new DelegatingByTypeSerializer(values, true)));
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

    /** An Avro record as the JSON text of its toString(): readable on the DLT without the registry. */
    private static Serializer<Object> avroAsJsonText() {
        return (topic, data) -> data == null ? null : data.toString().getBytes(StandardCharsets.UTF_8);
    }
}

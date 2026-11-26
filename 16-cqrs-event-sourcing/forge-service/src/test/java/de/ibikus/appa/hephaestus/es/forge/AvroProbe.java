package de.ibikus.appa.hephaestus.es.forge;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Plain Kafka clients with the Apicurio Avro SerDe: the test plays Ordering — any producer version (a GenericRecord
 * of the 1.0.0 schema, or a generated class) — independent of the application's KafkaTemplate.
 */
public final class AvroProbe {

    private AvroProbe() {}

    /** Writes with the registered schema {@code version} (as a producer of that contract version would). */
    public static void send(
            String bootstrap, String registryUrl, String topic, String key, Object value, String version) {
        var config = new HashMap<String, Object>(serde(registryUrl));
        config.put("apicurio.registry.artifact.version", version);
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.apicurio.registry.serde.avro.AvroKafkaSerializer");
        try (var producer = new KafkaProducer<String, Object>(config)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The value of the first record with {@code key} within {@code timeout}, as a generated Avro class. */
    public static Optional<Object> read(
            String bootstrap, String registryUrl, String topic, String key, Duration timeout) {
        var config = new HashMap<String, Object>(serde(registryUrl));
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "probe-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "io.apicurio.registry.serde.avro.AvroKafkaDeserializer");
        try (var consumer = new KafkaConsumer<String, Object>(config)) {
            consumer.subscribe(List.of(topic));
            var deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        return Optional.of(record.value());
                    }
                }
            }
            return Optional.empty();
        }
    }

    /** Every record with {@code key} that arrives within {@code timeout} (waits the whole timeout). */
    public static List<Object> readAll(
            String bootstrap, String registryUrl, String topic, String key, Duration timeout) {
        var config = new HashMap<String, Object>(serde(registryUrl));
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "probe-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "io.apicurio.registry.serde.avro.AvroKafkaDeserializer");
        var values = new java.util.ArrayList<Object>();
        try (var consumer = new KafkaConsumer<String, Object>(config)) {
            consumer.subscribe(List.of(topic));
            var deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        values.add(record.value());
                    }
                }
            }
        }
        return values;
    }

    private static Map<String, Object> serde(String registryUrl) {
        return Map.of(
                "apicurio.registry.url", registryUrl + "/apis/registry/v3",
                "apicurio.registry.auto-register", false,
                "apicurio.registry.use-specific-avro-reader", true,
                "apicurio.registry.headers.enabled", false);
    }
}

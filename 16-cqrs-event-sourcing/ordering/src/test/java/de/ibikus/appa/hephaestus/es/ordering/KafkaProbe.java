package de.ibikus.appa.hephaestus.es.ordering;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Plain Kafka clients with String (de)serializers: raw bytes in (poison records) and out (the DLT as sent). */
public final class KafkaProbe {

    private KafkaProbe() {}

    /** The value of the first record with {@code key} within {@code timeout}, or empty. */
    public static Optional<String> read(String bootstrap, String topic, String key, Duration timeout) {
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG,
                "probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class))) {
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

    public static void send(String bootstrap, String topic, String key, String value) {
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The value bytes of the first record with {@code key}, exactly as on the topic. */
    public static Optional<byte[]> readBytes(String bootstrap, String topic, String key, Duration timeout) {
        try (var consumer = new KafkaConsumer<String, byte[]>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG,
                "probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class))) {
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
}

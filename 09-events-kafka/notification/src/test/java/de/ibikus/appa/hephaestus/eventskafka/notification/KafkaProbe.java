package de.ibikus.appa.hephaestus.eventskafka.notification;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.KafkaTemplate;

/** Test helpers: send raw events like Ordering does, read a topic from the start, wait for a condition. */
public final class KafkaProbe {

    private KafkaProbe() {}

    public static UUID sendOrderPlaced(KafkaTemplate<String, String> kafka, String customer) {
        var eventId = UUID.randomUUID();
        send(kafka, customer, eventId);
        return eventId;
    }

    public static void send(KafkaTemplate<String, String> kafka, String customer, UUID eventId) {
        var json = """
                {"eventId":"%s","orderId":"%s","customerId":"%s","lines":[{"sku":"ANVIL-01","quantity":2}],\
                "occurredAt":"2026-11-18T10:00:00Z"}""".formatted(eventId, UUID.randomUUID(), customer);
        sendRaw(kafka, customer, json);
    }

    public static void sendRaw(KafkaTemplate<String, String> kafka, String key, String value) {
        var record = new ProducerRecord<>("orders.events", key, value);
        record.headers().add("eventType", "OrderPlaced".getBytes(StandardCharsets.UTF_8));
        kafka.send(record).join();
    }

    /** All records of the topic with this key, read from the beginning (stops after one empty second). */
    public static List<ConsumerRecord<String, String>> read(String bootstrap, String topic, String key) {
        var props = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG,
                "probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        var found = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(topic));
            var empty = 0;
            var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (empty < 4 && System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(250));
                if (records.isEmpty() && consumer.assignment().size() > 0) {
                    empty++;
                }
                records.forEach(r -> {
                    if (key.equals(r.key())) {
                        found.add(r);
                    }
                });
            }
        }
        return found;
    }

    /** Waits up to {@code timeout} for the condition; returns whether it became true. */
    public static boolean await(Duration timeout, BooleanSupplier condition) {
        var deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}

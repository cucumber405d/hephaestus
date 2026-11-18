package de.ibikus.appa.hephaestus.observability.inventory;

import de.ibikus.appa.hephaestus.observability.inventory.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.inventory.messaging.SagaMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.KafkaTemplate;

/** Test helpers: send saga messages like the other services do, read a topic from the start, wait. */
public final class Probe {

    private Probe() {}

    public static SagaMessage message(String type, String saga, String customer, String amount, String sku, int qty) {
        return new SagaMessage(
                UUID.randomUUID(),
                type,
                UUID.randomUUID(),
                saga,
                customer,
                new BigDecimal(amount),
                List.of(new SagaMessage.Line(sku, qty)),
                null,
                Instant.now());
    }

    public static void send(KafkaTemplate<String, String> kafka, Messages messages, String topic, SagaMessage m) {
        kafka.send(topic, m.orderId().toString(), messages.write(m)).join();
    }

    /** Messages about the order on the topic, read from the beginning (stops after half a quiet second, max 10 s). */
    public static List<SagaMessage> read(String bootstrap, Messages messages, String topic, UUID orderId) {
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
        var found = new ArrayList<SagaMessage>();
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(topic));
            var quiet = 0;
            var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (quiet < 2 && System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(250));
                if (records.isEmpty() && !consumer.assignment().isEmpty()) {
                    quiet++;
                }
                records.forEach(r -> {
                    if (orderId.toString().equals(r.key())) {
                        found.add(messages.read(r.value()));
                    }
                });
            }
        }
        return found;
    }

    public static List<String> types(List<SagaMessage> messages) {
        return messages.stream().map(SagaMessage::type).toList();
    }

    public static boolean await(Duration timeout, BooleanSupplier condition) {
        var deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}

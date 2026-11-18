package de.ibikus.appa.hephaestus.observability.delivery;

import de.ibikus.appa.hephaestus.observability.delivery.messaging.Messages;
import de.ibikus.appa.hephaestus.observability.delivery.messaging.SagaMessage;
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

    /** A message read back, with the trace id of its {@code traceparent} header ({@code null} without one). */
    public record Received(SagaMessage message, String traceId) {}

    /** Messages about the order on the topic, read from the beginning (stops after half a quiet second, max 10 s). */
    public static List<SagaMessage> read(String bootstrap, Messages messages, String topic, UUID orderId) {
        return readWithTrace(bootstrap, messages, topic, orderId).stream()
                .map(Received::message)
                .toList();
    }

    /** Like {@link #read}, with the trace id each message carries in its Kafka headers. */
    public static List<Received> readWithTrace(String bootstrap, Messages messages, String topic, UUID orderId) {
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
        var found = new ArrayList<Received>();
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
                        found.add(new Received(messages.read(r.value()), traceId(r.headers().lastHeader("traceparent"))));
                    }
                });
            }
        }
        return found;
    }

    /** The trace id of a W3C traceparent header: {@code 00-<trace id>-<span id>-<flags>}. */
    static String traceId(org.apache.kafka.common.header.Header traceparent) {
        if (traceparent == null) {
            return null;
        }
        var parts = new String(traceparent.value(), java.nio.charset.StandardCharsets.UTF_8).split("-");
        return parts.length == 4 ? parts[1] : null;
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

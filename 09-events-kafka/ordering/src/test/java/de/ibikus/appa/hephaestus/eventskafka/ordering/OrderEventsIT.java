package de.ibikus.appa.hephaestus.eventskafka.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The whole Ordering on PostgreSQL and a real Kafka: what lands in orders.events, read back by a plain consumer. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainers.class, KafkaTestcontainers.class})
class OrderEventsIT {

    @Autowired
    MockMvcTester mvc;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @Test
    void orderPlacedIsPublishedKeyedByCustomerWithItsType() {
        var customer = "it-" + UUID.randomUUID();
        var orderId = place(customer);

        var records = readFor(customer, 1);

        var record = records.getFirst();
        assertThat(record.key()).isEqualTo(customer);
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo("OrderPlaced");
        assertThat(record.value())
                .contains("\"orderId\":\"" + orderId + "\"")
                .contains("\"customerId\":\"" + customer + "\"")
                .contains("\"eventId\":")
                .contains("\"sku\":\"ANVIL-01\"")
                .doesNotContain("@class");
    }

    @Test
    void eventsOfOneCustomerShareAPartition() {
        var customer = "it-" + UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            place(customer);
        }

        var records = readFor(customer, 4);

        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.getFirst().partition());
    }

    @Test
    void republishSendsTheSameEventIdAgain() {
        var customer = "it-" + UUID.randomUUID();
        var orderId = place(customer);

        assertThat(mvc.post().uri("/admin/republish/{id}", orderId).exchange()).hasStatus(HttpStatus.ACCEPTED);

        var records = readFor(customer, 2);
        assertThat(eventId(records.get(0).value())).isEqualTo(eventId(records.get(1).value()));
    }

    private String place(String customer) {
        var result = mvc.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":"%s","lines":[{"sku":"ANVIL-01","quantity":1}]}""".formatted(customer))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result.getResponse().getHeader("Location").replaceAll(".*/", "");
    }

    /** Reads orders.events from the beginning until it has {@code count} records of the customer (10 s at most). */
    private List<ConsumerRecord<String, String>> readFor(String customer, int count) {
        var props = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        var found = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of("orders.events"));
            var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (found.size() < count && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    if (customer.equals(record.key())) {
                        found.add(record);
                    }
                }
            }
        }
        assertThat(found).hasSize(count);
        return found;
    }

    private static String eventId(String json) {
        return json.replaceAll(".*\"eventId\":\"([^\"]+)\".*", "$1");
    }
}

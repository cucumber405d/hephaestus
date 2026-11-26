package de.ibikus.appa.hephaestus.es.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import de.ibikus.appa.hephaestus.contracts.events.WorkOrderRequest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/** The whole service over REST with a stub Forge on gRPC: the quote lands on the order and in the Avro request. */
// The relay of 14 puts the request on the topic
@SpringBootTest
@AutoConfigureMockMvc
@Import(Containers.class)
class OrderingFlowIT {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final int FORGE_PORT = FreePort.find();
    static final StubForge FORGE = new StubForge(FORGE_PORT);

    @DynamicPropertySource
    static void forge(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.client.channel.forge.target", () -> "static://localhost:" + FORGE_PORT);
    }

    @AfterAll
    static void stopForge() {
        FORGE.close();
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    GenericContainer<?> apicurio;

    @Test
    void customOrderSendsAvroRequestWithQuote() throws Exception {
        var id = place("{\"customerId\":\"c1\",\"custom\":true,\"material\":\"IRON-01\",\"quantity\":5}");

        assertThat(mvc.get().uri("/api/orders/{id}", id))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("ACCEPTED");
        assertThat(mvc.get().uri("/api/orders/{id}", id))
                .bodyJson()
                .extractingPath("$.priceCents")
                .isEqualTo(6000);
        assertThat(mvc.get().uri("/api/orders/{id}", id))
                .bodyJson()
                .extractingPath("$.leadTimeDays")
                .isEqualTo(3);
        var record = AvroProbe.read(
                kafka.getBootstrapServers(), Containers.url(apicurio), "es.forge.requests", id.toString(), WAIT);
        assertThat(record).hasValueSatisfying(value -> {
            var request = (WorkOrderRequest) value;
            assertThat(request.getMaterial()).isEqualTo("IRON-01");
            assertThat(request.getQuantity()).isEqualTo(5);
            assertThat(request.getQuoteId()).isNotBlank();
            assertThat(request.getPriceCents()).isEqualTo(6000L);
        });
    }

    @Test
    void stockOrderIsReadyAtOnce() throws Exception {
        var id = place("{\"customerId\":\"c1\",\"sku\":\"HAMMER-01\"}");

        assertThat(mvc.get().uri("/api/orders/{id}", id))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("READY");
    }

    private UUID place(String body) throws Exception {
        var result = mvc.post()
                .uri("/api/orders")
                .contentType("application/json")
                .content(body)
                .exchange();
        assertThat(result).hasStatus(202);
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return UUID.fromString(id);
    }
}

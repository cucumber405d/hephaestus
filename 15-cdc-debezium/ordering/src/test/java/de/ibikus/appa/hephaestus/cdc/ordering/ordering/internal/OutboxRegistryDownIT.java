package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import de.ibikus.appa.hephaestus.cdc.ordering.Containers;
import de.ibikus.appa.hephaestus.cdc.ordering.FreePort;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal.QuoteClient.Quoted;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Debezium mode with the registry unreachable: the request cannot be serialized inside the order transaction, so
 * nothing is stored — no order, no outbox row — and the caller gets 503 to retry.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(Containers.class)
class OutboxRegistryDownIT {

    @DynamicPropertySource
    static void deadRegistry(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.kafka.properties.apicurio.registry.url",
                () -> "http://localhost:" + FreePort.find() + "/apis/registry/v3");
        registry.add("spring.kafka.properties.apicurio.registry.retry-count", () -> 0);
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    QuoteClient quotes;

    @Test
    void registryDownRejectsOrderWithoutOutboxRow() {
        doReturn(new Quoted("q-1", 1200, 3)).when(quotes).quote(anyString(), anyInt());
        var orders = count("ordering.customer_order");

        assertThat(mvc.post()
                        .uri("/api/orders")
                        .contentType("application/json")
                        .content("{\"customerId\":\"c1\",\"custom\":true,\"material\":\"IRON-01\",\"quantity\":1}"))
                .hasStatus(503)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("schema registry unavailable");
        assertThat(count("ordering.customer_order")).isEqualTo(orders);
        assertThat(count("ordering.outbox")).isZero();
    }

    private int count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Integer.class).single();
    }
}

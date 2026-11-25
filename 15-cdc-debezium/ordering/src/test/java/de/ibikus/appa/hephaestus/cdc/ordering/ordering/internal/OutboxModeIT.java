package de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import de.ibikus.appa.hephaestus.cdc.ordering.AvroProbe;
import de.ibikus.appa.hephaestus.cdc.ordering.Containers;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.OrderRequest;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.Orders;
import de.ibikus.appa.hephaestus.cdc.ordering.ordering.internal.QuoteClient.Quoted;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/** Modulith mode: the relay of 14 sends, and no outbox row is written — a running connector must not send twice. */
@SpringBootTest(properties = "outbox.mode=modulith")
@Import(Containers.class)
class OutboxModeIT {

    @Autowired
    Orders orders;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    GenericContainer<?> apicurio;

    @MockitoBean
    QuoteClient quotes;

    @Test
    void modulithModeWritesNoOutboxRow() {
        doReturn(new Quoted("q-1", 1200, 3)).when(quotes).quote(anyString(), anyInt());

        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 1));

        assertThat(AvroProbe.read(
                        kafka.getBootstrapServers(),
                        Containers.url(apicurio),
                        WorkOrderRequests.TOPIC,
                        id.toString(),
                        Duration.ofSeconds(15)))
                .isPresent();
        assertThat(jdbc.sql("select count(*) from ordering.outbox").query(Integer.class).single())
                .isZero();
    }
}

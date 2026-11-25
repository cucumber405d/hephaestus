package de.ibikus.appa.hephaestus.cdc.forge.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.cdc.forge.AvroProbe;
import de.ibikus.appa.hephaestus.cdc.forge.Containers;
import de.ibikus.appa.hephaestus.cdc.forge.FakeSupplier;
import de.ibikus.appa.hephaestus.cdc.forge.KafkaProbe;
import de.ibikus.appa.hephaestus.cdc.forge.SchemaRegistry;
import de.ibikus.appa.hephaestus.contracts.events.WorkOrderRequest;
import java.time.Duration;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/** cdc.forge.requests, the test playing Ordering: Avro in, once per customer order, any released producer version. */
@SpringBootTest
@Import(Containers.class)
class WorkOrderIntakeIT {

    static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    KafkaContainer kafka;

    @Autowired
    GenericContainer<?> apicurio;

    @Autowired
    JdbcClient jdbc;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @Test
    void requestCreatesWorkOrder() {
        var orderId = UUID.randomUUID();

        request(orderId, "IRON-01", 1);

        await().atMost(WAIT).until(() -> count(orderId) == 1);
    }

    @Test
    void duplicateRequestCreatesOneWorkOrder() {
        var orderId = UUID.randomUUID();
        request(orderId, "IRON-01", 1);
        request(orderId, "IRON-01", 1);
        await().atMost(WAIT).until(() -> count(orderId) == 1);
        // the second delivery has had its chance too: still one
        var other = UUID.randomUUID();
        request(other, "IRON-01", 1);
        await().atMost(WAIT).until(() -> count(other) == 1);

        assertThat(count(orderId)).isEqualTo(1);
    }

    @Test
    void v1RequestWithoutQuoteIsAccepted() throws Exception {
        // written by a 1.0.0 producer: the released schema, no quote fields (consumer upgraded first, BACKWARD)
        var v1 = new Schema.Parser()
                .parse(SchemaRegistry.CONTRACTS
                        .resolve("released/1.0.0/avro/work_order_request.avsc")
                        .toFile());
        var orderId = UUID.randomUUID();
        var old = new GenericData.Record(v1);
        old.put("orderId", orderId.toString());
        old.put("material", "IRON-01");
        old.put("quantity", 1);

        AvroProbe.send(
                kafka.getBootstrapServers(),
                Containers.url(apicurio),
                "cdc.forge.requests",
                orderId.toString(),
                old,
                SchemaRegistry.RELEASED);

        await().atMost(WAIT).until(() -> count(orderId) == 1);
    }

    @Test
    void poisonRecordGoesToDlt() {
        // not Avro at all: parked on cdc.forge.requests.DLT at once with its bytes, the partition moves on
        var key = UUID.randomUUID().toString();
        KafkaProbe.send(kafka.getBootstrapServers(), "cdc.forge.requests", key, "not avro");

        assertThat(KafkaProbe.read(kafka.getBootstrapServers(), "cdc.forge.requests.DLT", key, WAIT))
                .hasValue("not avro");
        // and the next request on the topic is still served
        var orderId = UUID.randomUUID();
        request(orderId, "IRON-01", 1);
        await().atMost(WAIT).until(() -> count(orderId) == 1);
    }

    @Test
    void malformedOrderIdGoesToDlt() {
        // readable Avro, but the listener refuses it (IllegalArgumentException, not retried): parked as JSON text
        var key = UUID.randomUUID().toString();
        var request = WorkOrderRequest.newBuilder()
                .setOrderId("not-a-uuid")
                .setMaterial("IRON-01")
                .setQuantity(1)
                .build();
        AvroProbe.send(
                kafka.getBootstrapServers(),
                Containers.url(apicurio),
                "cdc.forge.requests",
                key,
                request,
                SchemaRegistry.CURRENT);

        assertThat(KafkaProbe.read(kafka.getBootstrapServers(), "cdc.forge.requests.DLT", key, WAIT))
                .hasValueSatisfying(text -> assertThat(text).contains("not-a-uuid"));
        // and the partition moves on
        var orderId = UUID.randomUUID();
        request(orderId, "IRON-01", 1);
        await().atMost(WAIT).until(() -> count(orderId) == 1);
    }

    private void request(UUID orderId, String material, int quantity) {
        var request = WorkOrderRequest.newBuilder()
                .setOrderId(orderId.toString())
                .setMaterial(material)
                .setQuantity(quantity)
                .build();
        AvroProbe.send(
                kafka.getBootstrapServers(),
                Containers.url(apicurio),
                "cdc.forge.requests",
                orderId.toString(),
                request,
                SchemaRegistry.CURRENT);
    }

    private int count(UUID orderId) {
        return jdbc.sql("select count(*) from work_order where order_id = ?")
                .param(orderId)
                .query(Integer.class)
                .single();
    }
}

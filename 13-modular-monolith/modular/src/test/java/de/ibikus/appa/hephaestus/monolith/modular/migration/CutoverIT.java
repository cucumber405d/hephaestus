package de.ibikus.appa.hephaestus.monolith.modular.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.ibikus.appa.hephaestus.monolith.modular.Containers;
import de.ibikus.appa.hephaestus.monolith.modular.FakeSupplier;
import de.ibikus.appa.hephaestus.monolith.modular.KafkaProbe;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderRequested;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrders;
import de.ibikus.appa.hephaestus.monolith.modular.ordering.OrderRequest;
import de.ibikus.appa.hephaestus.monolith.modular.ordering.Orders;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The monolith's side of the Strangler Fig (variant A): where a new work order goes is decided when ordering publishes
 * it, and the decision survives the switch; rollback; decommission only when nothing is open.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(Containers.class)
class CutoverIT {

    static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Orders orders;

    @Autowired
    WorkOrders workOrders;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @AfterEach
    void reset() {
        jdbc.sql("update migration.forge_cutover set remote = false").update();
        jdbc.sql("update forge.lifecycle set decommissioned = false").update();
        // finish what the tests left open, so decommission cases start clean
        jdbc.sql("update forge.work_order set status = 'DONE' where status in ('PLANNED', 'IN_PROGRESS')")
                .update();
    }

    @Test
    void beforeCutoverStaysLocal() {
        var id = orders.place(custom());
        await().atMost(WAIT).until(() -> localWorkOrders(id) == 1);
        assertThat(KafkaProbe.read(kafka.getBootstrapServers(), "forge.requests", id.toString(), Duration.ofSeconds(3)))
                .isEmpty();
    }

    @Test
    void afterCutoverRequestGoesToKafka() {
        cutover(true);
        var id = orders.place(custom());
        var record = KafkaProbe.read(kafka.getBootstrapServers(), "forge.requests", id.toString(), WAIT);
        assertThat(record)
                .hasValueSatisfying(json -> assertThat(json)
                        .contains("\"orderId\":\"" + id + "\"")
                        .contains("\"material\":\"IRON-01\"")
                        .contains("\"quantity\":1"));
        assertThat(localWorkOrders(id)).isZero();
    }

    @Test
    void aLocalRequestStaysLocalAfterTheCutover() {
        // A request published before the switch but delivered after it (a slow listener, a redelivery after a
        // restart): the target is in the event's type, not in the flag at delivery time
        var id = UUID.randomUUID();
        cutover(true);
        transactions.executeWithoutResult(tx -> events.publishEvent(new WorkOrderRequested(id, "IRON-01", 1)));
        await().atMost(WAIT).until(() -> localWorkOrders(id) == 1);
        assertThat(KafkaProbe.read(kafka.getBootstrapServers(), "forge.requests", id.toString(), Duration.ofSeconds(3)))
                .isEmpty();
    }

    @Test
    void rollbackSendsNewOrdersLocalAgain() {
        cutover(true);
        cutover(false);
        var id = orders.place(custom());
        await().atMost(WAIT).until(() -> localWorkOrders(id) == 1);
    }

    @Test
    void decommissionRefusedBeforeCutover() {
        assertThat(mvc.post().uri("/admin/forge/decommission")).hasStatus(409);
        assertThat(workOrders.decommissioned()).isFalse();
    }

    @Test
    void decommissionRefusedWhileOpen() {
        var id = orders.place(custom());
        await().atMost(WAIT).until(() -> localWorkOrders(id) == 1);
        cutover(true);

        assertThat(mvc.post().uri("/admin/forge/decommission"))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.openLocalWorkOrders")
                .asNumber()
                .isEqualTo(1);
        assertThat(workOrders.decommissioned()).isFalse();

        var wo = jdbc.sql("select id from forge.work_order where order_id = ?")
                .param(id)
                .query(String.class)
                .single();
        await().atMost(WAIT)
                .until(() -> mvc.post()
                                .uri("/api/work-orders/{id}/start", wo)
                                .exchange()
                                .getResponse()
                                .getStatus()
                        == 200);
        assertThat(mvc.post().uri("/api/work-orders/{id}/complete", wo)).hasStatusOk();

        assertThat(mvc.post().uri("/admin/forge/decommission")).hasStatusOk();
        assertThat(mvc.get().uri("/api/work-orders")).hasStatus(410);
        assertThat(mvc.get().uri("/internal/work-orders"))
                .hasStatusOk()
                .bodyJson()
                .isEqualTo("[]");
        assertThat(cutoverResponse(false)).isEqualTo(409);
    }

    private void cutover(boolean remote) {
        assertThat(cutoverResponse(remote)).isEqualTo(200);
    }

    private int cutoverResponse(boolean remote) {
        return mvc.post()
                .uri("/admin/forge/cutover")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"remote\":" + remote + "}")
                .exchange()
                .getResponse()
                .getStatus();
    }

    private int localWorkOrders(UUID orderId) {
        return jdbc.sql("select count(*) from forge.work_order where order_id = ?")
                .param(orderId)
                .query(Integer.class)
                .single();
    }

    private static OrderRequest custom() {
        return new OrderRequest("c1", null, true, "IRON-01", 1);
    }
}

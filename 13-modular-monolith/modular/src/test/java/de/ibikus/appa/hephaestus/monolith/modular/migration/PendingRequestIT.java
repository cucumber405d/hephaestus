package de.ibikus.appa.hephaestus.monolith.modular.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.ibikus.appa.hephaestus.monolith.modular.Containers;
import de.ibikus.appa.hephaestus.monolith.modular.KafkaProbe;
import de.ibikus.appa.hephaestus.monolith.modular.ordering.OrderRequest;
import de.ibikus.appa.hephaestus.monolith.modular.ordering.Orders;
import de.ibikus.appa.hephaestus.monolith.modular.supplier.SupplierGateway;
import de.ibikus.appa.hephaestus.monolith.modular.supplier.SupplyResult.Delivered;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.kafka.KafkaContainer;

/**
 * A local request published before the cutover whose listener has not (successfully) run yet — a slow listener, a
 * crash, a failure: it is neither a work order nor gone. Decommission must wait for it, and when it is redelivered
 * (Modulith republish after a restart) it stays local (review C1, I4). The supplier mock throws to keep it pending.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(Containers.class)
class PendingRequestIT {

    static final Duration WAIT = Duration.ofSeconds(15);

    @MockitoBean
    SupplierGateway supplier;

    @Autowired
    Orders orders;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IncompleteEventPublications incomplete;

    @Autowired
    KafkaContainer kafka;

    @AfterEach
    void reset() {
        jdbc.sql("update migration.forge_cutover set remote = false").update();
        jdbc.sql("update forge.lifecycle set decommissioned = false").update();
        jdbc.sql("update forge.work_order set status = 'DONE' where status in ('PLANNED', 'IN_PROGRESS')")
                .update();
        jdbc.sql("delete from event_publication where completion_date is null").update();
        org.mockito.Mockito.reset(supplier);
    }

    @Test
    void aPendingLocalRequestBlocksDecommission() {
        var id = pendingLocalRequest();
        cutover(true);

        assertThat(mvc.post().uri("/admin/forge/decommission"))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.openLocalWorkOrders")
                .asNumber()
                .isEqualTo(1);
        assertThat(mvc.get().uri("/admin/forge"))
                .bodyJson()
                .extractingPath("$.openLocalWorkOrders")
                .asNumber()
                .isEqualTo(1);
        assertThat(localWorkOrders(id)).isZero();
    }

    @Test
    void aRepublishedLocalRequestStaysLocalAfterTheCutover() {
        var id = pendingLocalRequest();
        cutover(true);

        // doReturn, not when(...): when() would call the mock, which still throws
        doReturn(new Delivered(500)).when(supplier).order(anyString(), anyInt());
        incomplete.resubmitIncompletePublications(publication -> true);

        await().atMost(WAIT).until(() -> localWorkOrders(id) == 1);
        assertThat(KafkaProbe.read(kafka.getBootstrapServers(), "forge.requests", id.toString(), Duration.ofSeconds(3)))
                .isEmpty();
    }

    /** A custom order whose local work order cannot be created yet: the listener fails on the supplier. */
    private UUID pendingLocalRequest() {
        when(supplier.order(anyString(), anyInt())).thenThrow(new IllegalStateException("supplier exploded"));
        // more than the stock: Forge must ask the supplier
        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 500));
        verify(supplier, timeout(WAIT.toMillis())).order(anyString(), anyInt());
        await().atMost(WAIT)
                .until(() -> jdbc.sql("select count(*) from event_publication where completion_date is null"
                                        + " and event_type like '%.WorkOrderRequested'")
                                .query(Integer.class)
                                .single()
                        == 1);
        assertThat(localWorkOrders(id)).isZero();
        return id;
    }

    private void cutover(boolean remote) {
        assertThat(mvc.post()
                        .uri("/admin/forge/cutover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remote\":" + remote + "}"))
                .hasStatusOk();
    }

    private int localWorkOrders(UUID orderId) {
        return jdbc.sql("select count(*) from forge.work_order where order_id = ?")
                .param(orderId)
                .query(Integer.class)
                .single();
    }
}

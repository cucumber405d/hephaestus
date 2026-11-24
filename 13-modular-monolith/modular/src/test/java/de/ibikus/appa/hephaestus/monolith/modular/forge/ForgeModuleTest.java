package de.ibikus.appa.hephaestus.monolith.modular.forge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.ibikus.appa.hephaestus.monolith.modular.Containers;
import de.ibikus.appa.hephaestus.monolith.modular.supplier.SupplierGateway;
import de.ibikus.appa.hephaestus.monolith.modular.supplier.SupplyResult.PermanentFailure;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Forge alone (no ordering, no inventory; the supplier mocked): requests in, outcomes out — as events. */
@ApplicationModuleTest
@Import(Containers.class)
class ForgeModuleTest {

    @Autowired
    WorkOrders workOrders;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    SupplierGateway supplier;

    @Test
    void aRequestBecomesAPlannedWorkOrder(Scenario scenario) {
        var orderId = UUID.randomUUID();
        scenario.publish(new WorkOrderRequested(orderId, "IRON-01", 1))
                .andWaitForStateChange(() -> status(orderId), Objects::nonNull)
                .andVerify(status -> assertThat(status).isEqualTo("PLANNED"));
    }

    @Test
    void theSmithsCompletionLeavesAsAnEvent(Scenario scenario) {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "IRON-01", 1);
        var id = idOf(orderId);
        scenario.stimulate(() -> {
                    workOrders.start(id);
                    workOrders.complete(id);
                })
                .andWaitForEventOfType(WorkOrderCompleted.class)
                .matching(e -> e.orderId().equals(orderId))
                .toArriveAndVerify(e -> assertThat(e.workOrderId()).isEqualTo(id));
    }

    @Test
    void anUndeliverableMaterialFailsTheWorkOrder(Scenario scenario) {
        when(supplier.order(eq("MITHRIL-01"), anyInt()))
                .thenReturn(new PermanentFailure(PermanentFailure.NOT_DELIVERABLE));
        var orderId = UUID.randomUUID();
        scenario.publish(new WorkOrderRequested(orderId, "MITHRIL-01", 1))
                .andWaitForEventOfType(WorkOrderFailed.class)
                .matching(e -> e.orderId().equals(orderId))
                .toArriveAndVerify(e -> assertThat(e.reason()).isEqualTo("material not deliverable: MITHRIL-01"));
    }

    @Test
    void aRepeatedRequestCreatesOneWorkOrder() {
        var orderId = UUID.randomUUID();
        workOrders.request(orderId, "IRON-01", 1);
        workOrders.request(orderId, "IRON-01", 1);
        assertThat(jdbc.sql("select count(*) from forge.work_order where order_id = ?")
                        .param(orderId)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    private String status(UUID orderId) {
        return jdbc.sql("select status from forge.work_order where order_id = ?")
                .param(orderId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private String idOf(UUID orderId) {
        return jdbc.sql("select id from forge.work_order where order_id = ?")
                .param(orderId)
                .query(String.class)
                .single();
    }
}

package de.ibikus.appa.hephaestus.monolith.modular.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.monolith.modular.Containers;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderFailed;
import de.ibikus.appa.hephaestus.monolith.modular.forge.WorkOrderRequested;
import de.ibikus.appa.hephaestus.monolith.modular.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Ordering alone: it asks Forge by event and reacts to Forge's outcomes; inventory mocked, no forge beans. */
@ApplicationModuleTest
@Import(Containers.class)
class OrderingModuleTest {

    @Autowired
    Orders orders;

    @MockitoBean
    Inventory inventory;

    @Test
    void aCustomOrderAsksForgeByEvent(Scenario scenario) {
        scenario.stimulate(() -> orders.place(new OrderRequest("c1", null, true, "IRON-01", 2)))
                .andWaitForEventOfType(WorkOrderRequested.class)
                .toArriveAndVerify(e -> {
                    assertThat(e.material()).isEqualTo("IRON-01");
                    assertThat(e.quantity()).isEqualTo(2);
                });
    }

    @Test
    void forgesCompletionMakesTheOrderReady(Scenario scenario) {
        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 1));
        scenario.publish(new WorkOrderCompleted(id, "WO-000001"))
                .andWaitForStateChange(() -> orders.get(id).status(), "READY"::equals)
                .andVerify(status -> assertThat(status).isEqualTo("READY"));
    }

    @Test
    void forgesFailureRejectsTheOrderWithForgesReason(Scenario scenario) {
        var id = orders.place(new OrderRequest("c1", null, true, "MITHRIL-01", 1));
        scenario.publish(new WorkOrderFailed(id, "WO-000002", "material not deliverable: MITHRIL-01"))
                .andWaitForStateChange(() -> orders.get(id).status(), "REJECTED"::equals)
                .andVerify(status ->
                        assertThat(orders.get(id).reason()).isEqualTo("material not deliverable: MITHRIL-01"));
    }
}

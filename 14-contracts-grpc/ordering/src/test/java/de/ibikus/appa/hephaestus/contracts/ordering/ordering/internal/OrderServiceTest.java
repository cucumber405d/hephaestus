package de.ibikus.appa.hephaestus.contracts.ordering.ordering.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.ibikus.appa.hephaestus.contracts.ordering.Containers;
import de.ibikus.appa.hephaestus.contracts.ordering.inventory.Inventory;
import de.ibikus.appa.hephaestus.contracts.ordering.ordering.OrderRequest;
import de.ibikus.appa.hephaestus.contracts.ordering.ordering.Orders;
import de.ibikus.appa.hephaestus.contracts.ordering.ordering.internal.QuoteClient.Quoted;
import de.ibikus.appa.hephaestus.contracts.ordering.ordering.internal.QuoteClient.Refused;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Ordering alone: quote first, then accept and ask Forge by event — or reject with the quote's reason. */
@ApplicationModuleTest
@Import(Containers.class)
class OrderServiceTest {

    @Autowired
    Orders orders;

    @MockitoBean
    QuoteClient quotes;

    @MockitoBean
    Inventory inventory;

    @Test
    void quotedCustomOrderIsAcceptedWithPrice(Scenario scenario) {
        doReturn(new Quoted("q-1", 6000, 3)).when(quotes).quote("IRON-01", 5);

        scenario.stimulate(() -> orders.place(new OrderRequest("c1", null, true, "IRON-01", 5)))
                .andWaitForEventOfType(WorkOrderRequestedEvent.class)
                .toArriveAndVerify(event -> {
                    assertThat(event.quoteId()).isEqualTo("q-1");
                    assertThat(event.priceCents()).isEqualTo(6000);
                    var order = orders.get(event.orderId());
                    assertThat(order.status()).isEqualTo("ACCEPTED");
                    assertThat(order.priceCents()).isEqualTo(6000);
                    assertThat(order.leadTimeDays()).isEqualTo(3);
                });
    }

    @Test
    void quoteUnavailableRejectsWithoutRequest(PublishedEvents events) {
        doReturn(new Refused("quote unavailable")).when(quotes).quote(anyString(), anyInt());

        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 1));

        assertThat(orders.get(id).status()).isEqualTo("REJECTED");
        assertThat(orders.get(id).reason()).isEqualTo("quote unavailable");
        assertThat(events.ofType(WorkOrderRequestedEvent.class)).isEmpty();
    }

    @Test
    void unknownMaterialRejected() {
        doReturn(new Refused("unknown material: FOO")).when(quotes).quote("FOO", 1);

        var id = orders.place(new OrderRequest("c1", null, true, "FOO", 1));

        assertThat(orders.get(id).reason()).isEqualTo("unknown material: FOO");
    }

    @Test
    void invalidQuantityRejected() {
        doReturn(new Refused("invalid quantity")).when(quotes).quote("IRON-01", 0);

        var id = orders.place(new OrderRequest("c1", null, true, "IRON-01", 0));

        assertThat(orders.get(id).status()).isEqualTo("REJECTED");
        assertThat(orders.get(id).reason()).isEqualTo("invalid quantity");
    }

    @Test
    void stockOrderSkipsQuote() {
        doReturn(true).when(inventory).reserve("HAMMER-01", 1);

        var id = orders.place(new OrderRequest("c1", "HAMMER-01", null, null, null));

        assertThat(orders.get(id).status()).isEqualTo("READY");
        verify(quotes, never()).quote(anyString(), anyInt());
    }
}

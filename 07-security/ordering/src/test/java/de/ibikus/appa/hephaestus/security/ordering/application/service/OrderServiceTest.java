package de.ibikus.appa.hephaestus.security.ordering.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.security.ordering.application.fakes.FakeInventory;
import de.ibikus.appa.hephaestus.security.ordering.application.fakes.InMemoryOrderRepository;
import de.ibikus.appa.hephaestus.security.ordering.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.security.ordering.domain.InvalidValueException;
import de.ibikus.appa.hephaestus.security.ordering.domain.InventoryUnavailableException;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderLine;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderNotFoundException;
import de.ibikus.appa.hephaestus.security.ordering.domain.OrderStatus;
import de.ibikus.appa.hephaestus.security.ordering.domain.Requester;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private static final String ALICE = "alice-sub";

    private final FakeInventory inventory = new FakeInventory();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final OrderService service =
            new OrderService(inventory, orders, Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void placedOrderIsConfirmedWithItsReservation() {
        var order = service.place(ALICE, List.of(new OrderLine("ANVIL-01", 2)));

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.customerId()).isEqualTo(ALICE);
        assertThat(order.reservationId()).isNotNull();
        assertThat(orders.findById(order.id())).contains(order);
    }

    @Test
    void duplicateSkusAreMergedBeforeReserving() {
        var order = service.place(ALICE, List.of(new OrderLine("ANVIL-01", 2), new OrderLine("ANVIL-01", 3)));

        assertThat(order.lines()).containsExactly(new OrderLine("ANVIL-01", 5));
        assertThat(inventory.reservations()).containsExactly(List.of(new OrderLine("ANVIL-01", 5)));
    }

    @Test
    void noOrderWhenStockIsShort() {
        inventory.failWith(new InsufficientStockException("TONGS-01"));

        assertThatThrownBy(() -> service.place(ALICE, List.of(new OrderLine("TONGS-01", 1))))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(orders.size()).isZero();
    }

    @Test
    void noOrderWhenInventoryIsUnavailable() {
        inventory.failWith(new InventoryUnavailableException("down", null));

        assertThatThrownBy(() -> service.place(ALICE, List.of(new OrderLine("ANVIL-01", 1))))
                .isInstanceOf(InventoryUnavailableException.class);
        assertThat(orders.size()).isZero();
    }

    @Test
    void ownerReadsOwnOrder() {
        var order = service.place(ALICE, List.of(new OrderLine("ANVIL-01", 1)));

        assertThat(service.get(order.id(), new Requester(ALICE, false))).isEqualTo(order);
    }

    @Test
    void foreignOrderLooksExactlyLikeAMissingOne() {
        var order = service.place(ALICE, List.of(new OrderLine("ANVIL-01", 1)));

        assertThatThrownBy(() -> service.get(order.id(), new Requester("carol-sub", false)))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order " + order.id() + " not found");
    }

    @Test
    void adminReadsAnyOrder() {
        var order = service.place(ALICE, List.of(new OrderLine("ANVIL-01", 1)));

        assertThat(service.get(order.id(), new Requester("admin-sub", true))).isEqualTo(order);
    }

    @Test
    void blankCustomerIsRejectedBeforeReserving() {
        assertThatThrownBy(() -> service.place(" ", List.of(new OrderLine("ANVIL-01", 1))))
                .isInstanceOf(InvalidValueException.class);
        assertThat(inventory.reservations()).isEmpty();
    }

    @Test
    void emptyOrderIsInvalid() {
        assertThatThrownBy(() -> service.place(ALICE, List.of())).isInstanceOf(InvalidValueException.class);
    }
}

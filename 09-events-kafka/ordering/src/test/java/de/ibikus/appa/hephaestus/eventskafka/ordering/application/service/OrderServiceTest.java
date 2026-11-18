package de.ibikus.appa.hephaestus.eventskafka.ordering.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.fakes.FakeInventory;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.fakes.FakeOrderEvents;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.EventPublishException;
import de.ibikus.appa.hephaestus.eventskafka.ordering.application.fakes.InMemoryOrderRepository;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.InvalidValueException;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.InventoryUnavailableException;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderLine;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private final FakeInventory inventory = new FakeInventory();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final FakeOrderEvents events = new FakeOrderEvents();
    private final OrderService service =
            new OrderService(inventory, orders, events, Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void placedOrderIsConfirmedWithItsReservation() {
        var order = service.place("alice", List.of(new OrderLine("ANVIL-01", 2)));

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.reservationId()).isNotNull();
        assertThat(orders.findById(order.id())).contains(order);
    }

    @Test
    void duplicateSkusAreMergedBeforeReserving() {
        var order = service.place("alice", List.of(new OrderLine("ANVIL-01", 2), new OrderLine("ANVIL-01", 3)));

        assertThat(order.lines()).containsExactly(new OrderLine("ANVIL-01", 5));
        assertThat(inventory.reservations()).containsExactly(List.of(new OrderLine("ANVIL-01", 5)));
    }

    @Test
    void noOrderWhenStockIsShort() {
        inventory.failWith(new InsufficientStockException("TONGS-01"));

        assertThatThrownBy(() -> service.place("alice", List.of(new OrderLine("TONGS-01", 1))))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(orders.size()).isZero();
    }

    @Test
    void noOrderWhenInventoryIsUnavailable() {
        inventory.failWith(new InventoryUnavailableException("down", null));

        assertThatThrownBy(() -> service.place("alice", List.of(new OrderLine("ANVIL-01", 1))))
                .isInstanceOf(InventoryUnavailableException.class);
        assertThat(orders.size()).isZero();
    }

    @Test
    void emptyOrderIsInvalid() {
        assertThatThrownBy(() -> service.place("alice", List.of())).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void placedOrderIsPublishedAfterItWasStored() {
        var order = service.place("alice", List.of(new OrderLine("ANVIL-01", 1)));

        assertThat(events.published()).containsExactly(order);
        assertThat(order.customerId()).isEqualTo("alice");
        assertThat(order.eventId()).isNotNull();
    }

    @Test
    void failedPublishLeavesTheOrderStoredDualWrite() {
        events.brokerDown();

        assertThatThrownBy(() -> service.place("alice", List.of(new OrderLine("ANVIL-01", 1))))
                .isInstanceOf(EventPublishException.class);
        assertThat(orders.size()).isEqualTo(1);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void republishSendsTheSameEventAgain() {
        var order = service.place("alice", List.of(new OrderLine("ANVIL-01", 1)));

        service.republish(order.id());

        assertThat(events.published()).hasSize(2);
        assertThat(events.published().get(1).eventId()).isEqualTo(order.eventId());
    }
}

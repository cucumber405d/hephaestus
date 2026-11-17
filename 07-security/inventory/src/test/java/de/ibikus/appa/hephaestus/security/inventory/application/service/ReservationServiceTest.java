package de.ibikus.appa.hephaestus.security.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.security.inventory.application.fakes.InMemoryReservationRepository;
import de.ibikus.appa.hephaestus.security.inventory.application.fakes.InMemoryStockRepository;
import de.ibikus.appa.hephaestus.security.inventory.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.security.inventory.domain.InvalidValueException;
import de.ibikus.appa.hephaestus.security.inventory.domain.ReservationLine;
import de.ibikus.appa.hephaestus.security.inventory.domain.UnknownSkuException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {

    private final InMemoryStockRepository stock = new InMemoryStockRepository();
    private final InMemoryReservationRepository reservations = new InMemoryReservationRepository();
    private final ReservationService service = new ReservationService(
            stock, reservations, Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void reservesAllLines() {
        var result = service.reserve(
                UUID.randomUUID(), List.of(new ReservationLine("ANVIL-01", 2), new ReservationLine("HAMMER-01", 5)));

        assertThat(result.created()).isTrue();
        assertThat(stock.findBySku("ANVIL-01").orElseThrow().reserved()).isEqualTo(2);
        assertThat(stock.findBySku("HAMMER-01").orElseThrow().reserved()).isEqualTo(5);
    }

    @Test
    void repeatedOrderReturnsTheFirstReservationWithoutReservingAgain() {
        var orderId = UUID.randomUUID();
        var first = service.reserve(orderId, List.of(new ReservationLine("ANVIL-01", 2)));

        var second = service.reserve(orderId, List.of(new ReservationLine("ANVIL-01", 2)));

        assertThat(second.created()).isFalse();
        assertThat(second.reservation()).isEqualTo(first.reservation());
        assertThat(stock.findBySku("ANVIL-01").orElseThrow().reserved()).isEqualTo(2);
        assertThat(reservations.size()).isEqualTo(1);
    }

    @Test
    void duplicateSkusInOneRequestAreAddedUp() {
        var result = service.reserve(
                UUID.randomUUID(), List.of(new ReservationLine("ANVIL-01", 2), new ReservationLine("ANVIL-01", 3)));

        assertThat(result.reservation().lines()).containsExactly(new ReservationLine("ANVIL-01", 5));
        assertThat(stock.findBySku("ANVIL-01").orElseThrow().reserved()).isEqualTo(5);
    }

    @Test
    void allOrNothingWhenOneLineIsShort() {
        var lines = List.of(new ReservationLine("ANVIL-01", 1), new ReservationLine("SWORD-01", 3));

        assertThatThrownBy(() -> service.reserve(UUID.randomUUID(), lines))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(stock.findBySku("ANVIL-01").orElseThrow().reserved()).isZero();
        assertThat(reservations.size()).isZero();
    }

    @Test
    void unknownSkuIsRejected() {
        assertThatThrownBy(() -> service.reserve(UUID.randomUUID(), List.of(new ReservationLine("NOPE-01", 1))))
                .isInstanceOf(UnknownSkuException.class);
    }

    @Test
    void emptyReservationIsInvalid() {
        assertThatThrownBy(() -> service.reserve(UUID.randomUUID(), List.of()))
                .isInstanceOf(InvalidValueException.class);
    }
}

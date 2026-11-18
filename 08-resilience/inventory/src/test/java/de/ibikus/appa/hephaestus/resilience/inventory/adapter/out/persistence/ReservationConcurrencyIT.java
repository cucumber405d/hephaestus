package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.resilience.inventory.PostgresTestcontainers;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.ReserveStockUseCase;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.StockQuery;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.ReservationLine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real PostgreSQL row locks: concurrent reservations never oversell, repeats never double-reserve. */
@SpringBootTest
@Import(PostgresTestcontainers.class)
class ReservationConcurrencyIT {

    @Autowired
    ReserveStockUseCase reservations;

    @Autowired
    StockQuery stock;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void twoOrdersForTheLastUnitsOnlyOneWins() throws Exception {
        jdbc.update("insert into stock_item (sku, name, on_hand, reserved) values ('RACE-01', 'Race', 2, 0)");

        var outcomes = runConcurrently(
                () -> reservations.reserve(UUID.randomUUID(), List.of(new ReservationLine("RACE-01", 2))),
                () -> reservations.reserve(UUID.randomUUID(), List.of(new ReservationLine("RACE-01", 2))));

        assertThat(outcomes).containsExactlyInAnyOrder("ok", InsufficientStockException.class.getSimpleName());
        assertThat(stock.get("RACE-01").reserved()).isEqualTo(2);
    }

    @Test
    void concurrentRepeatsOfOneOrderReserveOnce() throws Exception {
        jdbc.update("insert into stock_item (sku, name, on_hand, reserved) values ('TWICE-01', 'Twice', 5, 0)");
        var orderId = UUID.randomUUID();

        var outcomes = runConcurrently(
                () -> reservations.reserve(orderId, List.of(new ReservationLine("TWICE-01", 1))),
                () -> reservations.reserve(orderId, List.of(new ReservationLine("TWICE-01", 1))));

        assertThat(outcomes).containsExactly("ok", "ok");
        assertThat(stock.get("TWICE-01").reserved()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reservation where order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
    }

    @Test
    void seedIsMigrated() {
        assertThat(stock.get("ANVIL-01").onHand()).isEqualTo(10);
        assertThat(stock.get("TONGS-01").available()).isZero();
    }

    @SafeVarargs
    private static List<String> runConcurrently(java.util.concurrent.Callable<Object>... calls) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.length)) {
            List<Future<String>> futures = new ArrayList<>();
            for (var call : calls) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        call.call();
                        return "ok";
                    } catch (RuntimeException e) {
                        return e.getClass().getSimpleName();
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (var future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }
}

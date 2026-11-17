package de.ibikus.appa.hephaestus.security.inventory.adapter.in.web;

import de.ibikus.appa.hephaestus.security.inventory.application.fakes.InMemoryReservationRepository;
import de.ibikus.appa.hephaestus.security.inventory.application.fakes.InMemoryStockRepository;
import de.ibikus.appa.hephaestus.security.inventory.application.service.ReservationService;
import de.ibikus.appa.hephaestus.security.inventory.application.service.StockService;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Real services on in-memory repositories; a fresh store per test context. */
@TestConfiguration(proxyBeanMethods = false)
class WebSliceFakes {

    @Bean
    InMemoryStockRepository stockRepository() {
        return new InMemoryStockRepository();
    }

    @Bean
    StockService stockService(InMemoryStockRepository stock) {
        return new StockService(stock);
    }

    @Bean
    ReservationService reservationService(InMemoryStockRepository stock) {
        return new ReservationService(stock, new InMemoryReservationRepository(), Clock.systemUTC());
    }
}

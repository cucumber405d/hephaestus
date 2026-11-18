package de.ibikus.appa.hephaestus.resilience.inventory.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.resilience.inventory.application.fakes.FakeSupplierOffers;
import de.ibikus.appa.hephaestus.resilience.inventory.application.fakes.InMemoryOfferCache;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.Mechanism;
import de.ibikus.appa.hephaestus.resilience.inventory.application.service.SupplyService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SupplyController.class)
@Import({ProblemDetailsAdvice.class, SupplyControllerTest.Fakes.class})
class SupplyControllerTest {

    private static final String PROBLEMS = "https://hephaestus.ibikus.de/problems/";

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {

        @Bean
        FakeSupplierOffers r4j() {
            return new FakeSupplierOffers(Mechanism.R4J);
        }

        @Bean
        SupplyService supplyService(FakeSupplierOffers r4j) {
            return new SupplyService(List.of(r4j), new InMemoryOfferCache(), Clock.systemUTC());
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    FakeSupplierOffers r4j;

    @BeforeEach
    void healthy() {
        r4j.succeed();
    }

    @Test
    void liveOfferWithItsSourceAndMechanism() {
        var result = mvc.get().uri("/api/supply/r4j/ANVIL-01").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.price").isEqualTo(120.00);
        assertThat(result).bodyJson().extractingPath("$.source").isEqualTo("live");
        assertThat(result).bodyJson().extractingPath("$.mechanism").isEqualTo("r4j");
    }

    @Test
    void unknownMechanismIs400() {
        var result = mvc.get().uri("/api/supply/hystrix/ANVIL-01").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "invalid-request");
    }

    @Test
    void unknownOfferIs404() {
        r4j.knowNothing();

        var result = mvc.get().uri("/api/supply/r4j/NOPE-01").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "unknown-offer");
    }

    @Test
    void outageWithoutCacheIs503WithRetryAfter() {
        r4j.beUnavailable();

        var result = mvc.get().uri("/api/supply/r4j/COLD-01").exchange();

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("10");
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(PROBLEMS + "supplier-unavailable");
    }

    @Test
    void outageWithCacheAnswersFromTheCache() {
        mvc.get().uri("/api/supply/r4j/HAMMER-01").exchange();
        r4j.beUnavailable();

        assertThat(mvc.get().uri("/api/supply/r4j/HAMMER-01").exchange())
                .bodyJson()
                .extractingPath("$.source")
                .isEqualTo("cache");
    }
}

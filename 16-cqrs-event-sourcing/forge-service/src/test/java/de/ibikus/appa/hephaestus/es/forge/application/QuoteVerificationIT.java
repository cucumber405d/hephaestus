package de.ibikus.appa.hephaestus.es.forge.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.es.forge.Containers;
import de.ibikus.appa.hephaestus.es.forge.FakeSupplier;
import de.ibikus.appa.hephaestus.es.forge.Journal;
import de.ibikus.appa.hephaestus.es.forge.MutableClock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The binding quote: a work order request is held to the quote Ordering accepted. */
@SpringBootTest
@Import({Containers.class, Journal.class, QuoteVerificationIT.Time.class})
class QuoteVerificationIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Time {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    WorkOrders workOrders;

    @Autowired
    Quotes quotes;

    @Autowired
    MutableClock clock;

    @Autowired
    Journal journal;

    @DynamicPropertySource
    static void supplier(DynamicPropertyRegistry registry) {
        registry.add("supplier.url", FakeSupplier::url);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void validQuoteCreatesWorkOrder() {
        var quote = quotes.quote("IRON-01", 1);
        var orderId = UUID.randomUUID();

        workOrders.request(orderId, "IRON-01", 1, Optional.of(new QuoteRef(quote.id(), quote.priceCents())));

        assertThat(status(orderId)).isEqualTo("ALLOCATED");
    }

    @Test
    void expiredQuoteFailsWorkOrder() {
        var quote = quotes.quote("IRON-01", 1);
        clock.advance(Duration.ofMinutes(16));
        var orderId = UUID.randomUUID();

        workOrders.request(orderId, "IRON-01", 1, Optional.of(new QuoteRef(quote.id(), quote.priceCents())));

        assertThat(status(orderId)).isEqualTo("FAILED");
        assertThat(reason(orderId)).isEqualTo("quote expired");
    }

    @Test
    void priceMismatchFailsWorkOrder() {
        var quote = quotes.quote("IRON-01", 1);
        var orderId = UUID.randomUUID();

        workOrders.request(orderId, "IRON-01", 1, Optional.of(new QuoteRef(quote.id(), quote.priceCents() - 1)));

        assertThat(reason(orderId)).isEqualTo("quote mismatch");
    }

    @Test
    void unknownQuoteFailsWorkOrder() {
        var orderId = UUID.randomUUID();

        workOrders.request(
                orderId,
                "IRON-01",
                1,
                Optional.of(new QuoteRef(UUID.randomUUID().toString(), 1200)));

        assertThat(reason(orderId)).isEqualTo("quote unknown");
    }

    @Test
    void redeliveryAfterExpiryChangesNothing() {
        var quote = quotes.quote("IRON-01", 1);
        var orderId = UUID.randomUUID();
        var ref = Optional.of(new QuoteRef(quote.id(), quote.priceCents()));
        workOrders.request(orderId, "IRON-01", 1, ref);
        clock.advance(Duration.ofMinutes(16));

        workOrders.request(orderId, "IRON-01", 1, ref);

        assertThat(journal.requests(orderId)).isEqualTo(1);
        assertThat(status(orderId)).isEqualTo("ALLOCATED");
        assertThat(failedEvents(orderId)).isZero();
    }

    private String status(UUID orderId) {
        return journal.status(orderId);
    }

    private String reason(UUID orderId) {
        return journal.workOrder(orderId).failureReason();
    }

    private int failedEvents(UUID orderId) {
        return journal.events(
                de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderDecider.idFor(orderId), "WorkOrderFailed");
    }
}

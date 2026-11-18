package de.ibikus.appa.hephaestus.resilience.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.resilience.inventory.application.fakes.FakeSupplierOffers;
import de.ibikus.appa.hephaestus.resilience.inventory.application.fakes.InMemoryOfferCache;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.Mechanism;
import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.SupplyAnswer.Source;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupplyServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    private final FakeSupplierOffers r4j = new FakeSupplierOffers(Mechanism.R4J);
    private final FakeSupplierOffers spring = new FakeSupplierOffers(Mechanism.SPRING);
    private final InMemoryOfferCache cache = new InMemoryOfferCache();
    private final SupplyService service =
            new SupplyService(List.of(r4j, spring), cache, Clock.fixed(T0, ZoneOffset.UTC));

    @Test
    void liveAnswerIsCachedAndMarkedLive() {
        var answer = service.offer(Mechanism.R4J, "ANVIL-01");

        assertThat(answer.source()).isEqualTo(Source.LIVE);
        assertThat(answer.fetchedAt()).isEqualTo(T0);
        assertThat(answer.mechanism()).isEqualTo(Mechanism.R4J);
        assertThat(cache.get("ANVIL-01")).isPresent();
    }

    @Test
    void theRequestedMechanismIsUsed() {
        service.offer(Mechanism.SPRING, "ANVIL-01");

        assertThat(spring.calls()).isEqualTo(1);
        assertThat(r4j.calls()).isZero();
    }

    @Test
    void unavailableSupplierFallsBackToTheCachedOffer() {
        service.offer(Mechanism.R4J, "ANVIL-01");
        r4j.beUnavailable();

        var answer = service.offer(Mechanism.R4J, "ANVIL-01");

        assertThat(answer.source()).isEqualTo(Source.CACHE);
        assertThat(answer.fetchedAt()).isEqualTo(T0);
        assertThat(answer.offer()).isEqualTo(FakeSupplierOffers.ANVIL);
    }

    @Test
    void unavailableSupplierAndEmptyCacheIsUnavailable() {
        r4j.beUnavailable();

        assertThatThrownBy(() -> service.offer(Mechanism.R4J, "ANVIL-01"))
                .isInstanceOf(SupplierUnavailableException.class);
    }

    @Test
    void unknownOfferIsPassedOnAndNotCached() {
        r4j.knowNothing();

        assertThatThrownBy(() -> service.offer(Mechanism.R4J, "NOPE-01")).isInstanceOf(UnknownOfferException.class);
        assertThat(cache.size()).isZero();
    }

    @Test
    void brokenCacheDoesNotBreakLiveAnswers() {
        cache.breakDown();

        assertThat(service.offer(Mechanism.R4J, "ANVIL-01").source()).isEqualTo(Source.LIVE);
    }

    @Test
    void brokenCacheDuringAnOutageIsUnavailable() {
        cache.breakDown();
        r4j.beUnavailable();

        assertThatThrownBy(() -> service.offer(Mechanism.R4J, "ANVIL-01"))
                .isInstanceOf(SupplierUnavailableException.class);
    }
}

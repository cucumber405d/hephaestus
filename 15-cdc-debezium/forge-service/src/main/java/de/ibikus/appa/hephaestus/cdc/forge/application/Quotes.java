package de.ibikus.appa.hephaestus.cdc.forge.application;

import de.ibikus.appa.hephaestus.cdc.forge.domain.PriceList;
import de.ibikus.appa.hephaestus.cdc.forge.domain.Quote;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Forge quotes a custom piece and later holds Ordering to the quote it accepted. */
@Service
public class Quotes {

    /** What a work order request's quote turned out to be; {@link #reason()} is the text Ordering receives. */
    public enum Verdict {
        OK(null),
        UNKNOWN("quote unknown"),
        EXPIRED("quote expired"),
        MISMATCH("quote mismatch");

        private final String reason;

        Verdict(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    private final QuoteStore store;
    private final PriceList prices;
    private final Duration ttl;
    private final Clock clock;

    public Quotes(QuoteStore store, PricingProperties pricing, Clock clock) {
        this.store = store;
        this.prices = new PriceList(pricing.priceList(), pricing.unitsPerDay());
        this.ttl = pricing.quoteTtl();
        this.clock = clock;
    }

    /**
     * @throws de.ibikus.appa.hephaestus.cdc.forge.domain.UnknownMaterial not on the price list
     * @throws de.ibikus.appa.hephaestus.cdc.forge.domain.InvalidQuantity below 1
     */
    @Transactional
    public Quote quote(String material, int quantity) {
        var price = prices.price(material, quantity);
        var quote = new Quote(
                UUID.randomUUID().toString(),
                material,
                quantity,
                price.priceCents(),
                price.leadTimeDays(),
                clock.instant().plus(ttl));
        store.save(quote);
        return quote;
    }

    /** The quote must exist, be for this material, quantity and price, and not be expired. */
    public Verdict verify(QuoteRef ref, String material, int quantity) {
        var quote = store.find(ref.quoteId()).orElse(null);
        if (quote == null) {
            return Verdict.UNKNOWN;
        }
        if (!quote.material().equals(material)
                || quote.quantity() != quantity
                || quote.priceCents() != ref.priceCents()) {
            return Verdict.MISMATCH;
        }
        if (clock.instant().isAfter(quote.expiresAt())) {
            return Verdict.EXPIRED;
        }
        return Verdict.OK;
    }
}

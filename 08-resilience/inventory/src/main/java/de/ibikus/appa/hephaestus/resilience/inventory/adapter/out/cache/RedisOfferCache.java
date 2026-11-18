package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.cache;

import de.ibikus.appa.hephaestus.resilience.inventory.application.port.out.OfferCache;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The last live offer per SKU in Redis ({@code offer:<sku>}, JSON, ten minutes). Redis trouble is logged and
 * treated as a miss: the cache may make answers better, never worse.
 */
@Component
class RedisOfferCache implements OfferCache {

    static final Duration TTL = Duration.ofMinutes(10);

    private static final Logger LOG = LoggerFactory.getLogger(RedisOfferCache.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    RedisOfferCache(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void put(SupplierOffer offer, Instant fetchedAt) {
        try {
            redis.opsForValue().set(key(offer.sku()), json.writeValueAsString(Entry.of(offer, fetchedAt)), TTL);
        } catch (RuntimeException e) {
            LOG.warn("Could not cache the offer for {}: {}", offer.sku(), e.toString());
        }
    }

    @Override
    public Optional<CachedOffer> get(String sku) {
        try {
            var value = redis.opsForValue().get(key(sku));
            return value == null
                    ? Optional.empty()
                    : Optional.of(json.readValue(value, Entry.class).toCached());
        } catch (RuntimeException e) {
            LOG.warn("Could not read the cached offer for {}: {}", sku, e.toString());
            return Optional.empty();
        }
    }

    static String key(String sku) {
        return "offer:" + sku;
    }

    record Entry(String sku, BigDecimal price, String currency, int leadTimeDays, int maxQuantity, Instant fetchedAt) {

        static Entry of(SupplierOffer offer, Instant fetchedAt) {
            return new Entry(
                    offer.sku(), offer.price(), offer.currency(), offer.leadTimeDays(), offer.maxQuantity(), fetchedAt);
        }

        CachedOffer toCached() {
            return new CachedOffer(new SupplierOffer(sku, price, currency, leadTimeDays, maxQuantity), fetchedAt);
        }
    }
}

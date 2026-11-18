package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

/** Real Redis: round trip, expiry set, unreadable entries behave like a miss. */
class RedisOfferCacheIT {

    static final GenericContainer<?> REDIS = startRedis();
    static final LettuceConnectionFactory CONNECTIONS = connect();
    static final StringRedisTemplate TEMPLATE = new StringRedisTemplate(CONNECTIONS);

    private final RedisOfferCache cache =
            new RedisOfferCache(TEMPLATE, JsonMapper.builder().build());

    @AfterAll
    static void stop() {
        CONNECTIONS.destroy();
        REDIS.stop();
    }

    @Test
    void offerRoundTripsWithItsFetchTime() {
        var offer = new SupplierOffer("ANVIL-01", new BigDecimal("120.00"), "EUR", 5, 20);
        var fetchedAt = Instant.parse("2026-11-18T10:00:00Z");

        cache.put(offer, fetchedAt);

        var cached = cache.get("ANVIL-01").orElseThrow();
        assertThat(cached.offer()).isEqualTo(offer);
        assertThat(cached.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(TEMPLATE.getExpire("offer:ANVIL-01")).isBetween(1L, 600L);
    }

    @Test
    void missingOrGarbledEntryIsAMiss() {
        TEMPLATE.opsForValue().set("offer:BROKEN-01", "not json");

        assertThat(cache.get("NOPE-01")).isEmpty();
        assertThat(cache.get("BROKEN-01")).isEmpty();
    }

    private static GenericContainer<?> startRedis() {
        var redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
        redis.start();
        return redis;
    }

    private static LettuceConnectionFactory connect() {
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}

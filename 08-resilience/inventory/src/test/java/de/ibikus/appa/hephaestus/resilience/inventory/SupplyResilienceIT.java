package de.ibikus.appa.hephaestus.resilience.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.resilience.inventory.StubSupplierServer.Mode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;

/**
 * The three mechanisms side by side on the same failures: the whole Inventory (PostgreSQL, Redis) calls a stub
 * supplier through Toxiproxy. Network faults come from Toxiproxy, HTTP faults and slowness from the stub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class SupplyResilienceIT {

    // Order matters: static fields are initialised top to bottom, and startToxiproxy() already uses HTTP and SUPPLIER
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final StubSupplierServer SUPPLIER = new StubSupplierServer();
    static final GenericContainer<?> REDIS = start(new GenericContainer<>("redis:8-alpine").withExposedPorts(6379));
    static final GenericContainer<?> TOXIPROXY = startToxiproxy();

    @DynamicPropertySource
    static void wiring(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        registry.add("supplier.url", () -> "http://" + TOXIPROXY.getHost() + ":" + TOXIPROXY.getMappedPort(8666));
        // Short open state so the recovery test does not wait ten seconds
        registry.add("supply.circuit-breaker.open", () -> "2s");
    }

    @AfterAll
    static void stop() {
        SUPPLIER.close();
    }

    @Autowired
    MockMvcTester mvc;

    /**
     * Every test leaves a healthy supplier and closed breakers behind: after the open interval, ten good calls per
     * mechanism close a half-open breaker and push earlier failures out of the count-based window.
     */
    @AfterEach
    void healAndCloseBreakers() throws Exception {
        SUPPLIER.mode(Mode.HEALTHY);
        toxiproxy("DELETE", "/proxies/supplier/toxics/latency", null);
        Thread.sleep(2200);
        for (var mechanism : List.of("r4j", "sccb", "spring")) {
            for (int i = 0; i < 10; i++) {
                get(mechanism, "WARM-01");
            }
        }
        SUPPLIER.resetCounters();
    }

    @ParameterizedTest
    @ValueSource(strings = {"r4j", "sccb", "spring"})
    void healthySupplierAnswersLive(String mechanism) {
        var result = get(mechanism, "ANVIL-01");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.source").isEqualTo("live");
        assertThat(result).bodyJson().extractingPath("$.mechanism").isEqualTo(mechanism);
    }

    @ParameterizedTest
    @ValueSource(strings = {"r4j", "sccb", "spring"})
    void networkLatencyBeyondTheTimeoutFallsBackToTheCache(String mechanism) throws Exception {
        get(mechanism, "LATE-01");
        toxiproxy("POST", "/proxies/supplier/toxics", """
                {"name":"latency","type":"latency","stream":"downstream","attributes":{"latency":1500}}""");
        var started = System.nanoTime();

        var result = get(mechanism, "LATE-01");

        assertThat(result).bodyJson().extractingPath("$.source").isEqualTo("cache");
        assertThat(millisSince(started)).isLessThan(5000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"r4j", "sccb", "spring"})
    void supplierErrorsFallBackToTheCache(String mechanism) {
        get(mechanism, "ERR-01");
        SUPPLIER.mode(Mode.FAILING);

        assertThat(get(mechanism, "ERR-01"))
                .bodyJson()
                .extractingPath("$.source")
                .isEqualTo("cache");
    }

    @ParameterizedTest
    @ValueSource(strings = {"r4j", "sccb", "spring"})
    void outageWithNothingCachedIs503(String mechanism) {
        SUPPLIER.mode(Mode.FAILING);

        var result = get(mechanism, "COLD-" + mechanism.toUpperCase());

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"r4j", "sccb", "spring"})
    void unknownOfferIs404AndNeverRetried(String mechanism) {
        assertThat(get(mechanism, "NOPE-01")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(SUPPLIER.calls()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"r4j", "sccb"})
    void openBreakerAnswersFromTheCacheWithoutCallingTheSupplier(String mechanism) throws Exception {
        get(mechanism, "OPEN-01");
        SUPPLIER.mode(Mode.FAILING);
        // r4j: 3 attempts per request, sccb: 1, both judge a window of 10 calls
        for (int i = 0; i < 10; i++) {
            get(mechanism, "OPEN-01");
        }
        var callsWhenOpen = SUPPLIER.calls();

        for (int i = 0; i < 5; i++) {
            var started = System.nanoTime();
            assertThat(get(mechanism, "OPEN-01"))
                    .bodyJson()
                    .extractingPath("$.source")
                    .isEqualTo("cache");
            // no timeout, no retries: far below the 1 s read timeout (generous for shared CI runners)
            assertThat(millisSince(started)).isLessThan(500);
        }
        assertThat(SUPPLIER.calls()).isEqualTo(callsWhenOpen);

        SUPPLIER.mode(Mode.HEALTHY);
        Thread.sleep(2200);
        assertThat(get(mechanism, "OPEN-01"))
                .bodyJson()
                .extractingPath("$.source")
                .isEqualTo("live");
    }

    @Test
    void springWithoutABreakerKeepsCallingTheSupplierThreeTimesPerRequest() {
        get("spring", "STORM-01");
        SUPPLIER.mode(Mode.FAILING);
        SUPPLIER.resetCounters();

        for (int i = 0; i < 5; i++) {
            assertThat(get("spring", "STORM-01"))
                    .bodyJson()
                    .extractingPath("$.source")
                    .isEqualTo("cache");
        }
        assertThat(SUPPLIER.calls()).isEqualTo(15);
    }

    @ParameterizedTest
    @ValueSource(strings = {"r4j", "spring"})
    void neverMoreThanTenCallsAtOnce(String mechanism) throws Exception {
        get(mechanism, "BUSY-01");
        SUPPLIER.mode(Mode.SLOW);
        SUPPLIER.resetCounters();

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> answers = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                Callable<Integer> call =
                        () -> get(mechanism, "BUSY-01").getResponse().getStatus();
                answers.add(pool.submit(call));
            }
            for (var answer : answers) {
                assertThat(answer.get()).isEqualTo(200);
            }
        }
        assertThat(SUPPLIER.maxInFlight()).isLessThanOrEqualTo(10);
    }

    private MvcTestResult get(String mechanism, String sku) {
        return mvc.get().uri("/api/supply/{mechanism}/{sku}", mechanism, sku).exchange();
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static void toxiproxy(String method, String path, String json) throws Exception {
        toxiproxy(TOXIPROXY, method, path, json);
    }

    private static void toxiproxy(GenericContainer<?> container, String method, String path, String json)
            throws Exception {
        var uri = URI.create("http://" + container.getHost() + ":" + container.getMappedPort(8474) + path);
        var body = json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json);
        HTTP.send(
                HttpRequest.newBuilder(uri)
                        .method(method, body)
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static GenericContainer<?> startToxiproxy() {
        Testcontainers.exposeHostPorts(SUPPLIER.port());
        var toxiproxy = start(new GenericContainer<>("ghcr.io/shopify/toxiproxy:2.12.0").withExposedPorts(8474, 8666));
        try {
            toxiproxy(toxiproxy, "POST", "/proxies", """
                    {"name":"supplier","listen":"0.0.0.0:8666","upstream":"host.testcontainers.internal:%d"}""".formatted(SUPPLIER.port()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return toxiproxy;
    }

    private static <T extends GenericContainer<?>> T start(T container) {
        container.start();
        return container;
    }
}

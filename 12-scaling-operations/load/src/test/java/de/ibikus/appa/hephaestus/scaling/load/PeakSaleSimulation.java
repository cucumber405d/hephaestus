package de.ibikus.appa.hephaestus.scaling.load;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * The peak sale (ADR-0004): an open model — buyers arrive at a rate and do not wait for each other. Warm-up, a
 * 30 s ramp, the peak, the cool-down. Every accepted order goes to the order log with its expected outcome; the
 * assertions are the HTTP part of ADR-0001 (the "no order lost" part is scripts/reconcile.sh).
 */
public class PeakSaleSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://orders.localtest.me");
    private static final double WARM_RATE = Double.parseDouble(System.getProperty("warmRate", "2"));
    private static final double PEAK_RATE = Double.parseDouble(System.getProperty("peakRate", "20"));
    private static final int PEAK_SECONDS = Integer.parseInt(System.getProperty("peakSeconds", "180"));
    // Customers are fresh per run (their credit is used by the confirmed orders of earlier runs)
    private static final String RUN_ID = System.getProperty("runId", Long.toString(System.currentTimeMillis()));
    private static final Path ORDERS_FILE = Path.of(System.getProperty("ordersFile", "target/orders.csv"));

    private final AtomicLong sequence = new AtomicLong();
    private final OrderLog log = new OrderLog(ORDERS_FILE);

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(BASE_URL).contentTypeHeader("application/json").acceptHeader("application/json");

    private final ScenarioBuilder buyer = scenario("buyer")
            .feed(Stream.generate(() -> {
                        var order = OrderMix.of(RUN_ID, sequence.getAndIncrement());
                        return Map.<String, Object>of(
                                "customer", order.customer(),
                                "style", order.style(),
                                "sku", order.sku(),
                                "expected", order.expected());
                    })
                    .iterator())
            .exec(http("place order")
                    .post("/api/orders")
                    .body(
                            StringBody(
                                    "{\"customerId\":\"#{customer}\",\"saga\":\"#{style}\",\"lines\":[{\"sku\":\"#{sku}\",\"quantity\":1}]}"))
                    .check(status().is(202), header("Location").saveAs("location")))
            .exec(session -> {
                if (!session.isFailed()) {
                    var location = session.getString("location");
                    log.append(
                            location.substring(location.lastIndexOf('/') + 1),
                            session.getString("style"),
                            session.getString("expected"));
                }
                return session;
            });

    {
        setUp(buyer.injectOpen(
                        constantUsersPerSec(WARM_RATE).during(60),
                        rampUsersPerSec(WARM_RATE).to(PEAK_RATE).during(30),
                        constantUsersPerSec(PEAK_RATE).during(PEAK_SECONDS),
                        rampUsersPerSec(PEAK_RATE).to(WARM_RATE).during(60)))
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95.0).lt(500),
                        global().responseTime().percentile(99.0).lt(2000),
                        global().failedRequests().percent().lt(0.1));
    }

    @Override
    public void after() {
        log.close();
    }
}

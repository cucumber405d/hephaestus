package de.ibikus.appa.hephaestus.monolith.traffic;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@code java -jar traffic.jar generate} — custom orders: ORDERS_URL, RATE (2/s), SECONDS (60), OUT (orders.csv),
 * HOST_HEADER (optional). {@code java -jar traffic.jar bot} — smiths until killed or BOT_SECONDS: MONOLITH_URL,
 * SERVICE_URL, GATEWAY_URL (optional; then everything goes there), HOST_HEADER, INTERVAL_MS (1000).
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws IOException {
        var mode = args.length > 0 ? args[0] : "";
        var http = new Http(env("HOST_HEADER", ""), 5);
        switch (mode) {
            case "generate" -> {
                try (var csv = new FileWriter(env("OUT", "orders.csv"), StandardCharsets.UTF_8, true)) {
                    var accepted = new Generator(http, env("ORDERS_URL", "http://localhost:18080"), csv)
                            .run(Double.parseDouble(env("RATE", "2")), Long.parseLong(env("SECONDS", "60")));
                    System.out.println("accepted=" + accepted);
                }
            }
            case "bot" -> {
                var bot = new SmithBot(
                        http,
                        env("MONOLITH_URL", "http://localhost:18080"),
                        env("SERVICE_URL", "http://localhost:18081"),
                        env("GATEWAY_URL", ""));
                var until = System.nanoTime() + Long.parseLong(env("BOT_SECONDS", "86400")) * 1_000_000_000L;
                var interval = Long.parseLong(env("INTERVAL_MS", "1000"));
                while (System.nanoTime() < until) {
                    try {
                        bot.round();
                    } catch (IllegalStateException e) {
                        System.err.println("round: " + e.getMessage());
                    }
                    Http.pause(interval);
                }
            }
            default -> {
                System.err.println("usage: java -jar traffic.jar generate|bot");
                System.exit(2);
            }
        }
    }

    private static String env(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}

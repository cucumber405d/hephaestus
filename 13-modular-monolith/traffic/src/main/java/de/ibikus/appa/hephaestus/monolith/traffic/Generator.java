package de.ibikus.appa.hephaestus.monolith.traffic;

import java.io.IOException;
import java.io.Writer;
import java.util.regex.Pattern;

/**
 * Places custom orders at {@code rate} per second for {@code seconds}; every accepted order (202) goes to the CSV
 * as {@code orderId,material,expected} — LF only, whatever the platform (12's CRLF lesson).
 */
final class Generator {

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"");

    private final Http http;
    private final String ordersUrl;
    private final Writer csv;

    Generator(Http http, String ordersUrl, Writer csv) {
        this.http = http;
        this.ordersUrl = ordersUrl;
        this.csv = csv;
    }

    /** Returns the number of accepted orders. */
    int run(double rate, long seconds) throws IOException {
        var interval = (long) (1_000_000_000L / rate);
        var start = System.nanoTime();
        var total = (long) (rate * seconds);
        var accepted = 0;
        for (long i = 0; i < total; i++) {
            var due = start + i * interval;
            var wait = due - System.nanoTime();
            if (wait > 0) {
                Http.pause(wait / 1_000_000);
            }
            if (place(i)) {
                accepted++;
            }
        }
        return accepted;
    }

    boolean place(long i) throws IOException {
        var mix = OrderMix.of(i);
        Http.Response response;
        try {
            // once: a retried POST could place a second order the CSV never hears of
            response = http.postOnce(ordersUrl + "/api/orders", mix.json(i));
        } catch (IllegalStateException e) {
            System.err.println("order " + i + " not placed: " + e.getMessage());
            return false;
        }
        var m = ID.matcher(response.body());
        if (response.status() != 202 || !m.find()) {
            System.err.println("order " + i + " refused: " + response.status() + " " + response.body());
            return false;
        }
        synchronized (csv) {
            csv.write(m.group(1) + "," + mix.material() + "," + mix.expected() + "\n");
            csv.flush();
        }
        return true;
    }
}

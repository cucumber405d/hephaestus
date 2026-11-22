package de.ibikus.appa.hephaestus.monolith.traffic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Plays the smiths: lists work orders, starts PLANNED ones, completes IN_PROGRESS ones. Routes like the Gateway's
 * HTTPRoute — {@code WO-} to the monolith, everything else (the list included) to the service — unless a gateway is
 * given, then everything goes there. 409 is an answer (material not there yet, done already), not an error.
 */
final class SmithBot {

    record Item(String id, String status) {}

    private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*}");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"");

    private final Http http;
    private final String monolithUrl;
    private final String serviceUrl;
    private final String gatewayUrl;

    SmithBot(Http http, String monolithUrl, String serviceUrl, String gatewayUrl) {
        this.http = http;
        this.monolithUrl = monolithUrl;
        this.serviceUrl = serviceUrl;
        this.gatewayUrl = gatewayUrl == null || gatewayUrl.isBlank() ? null : gatewayUrl;
    }

    String baseFor(String workOrderId) {
        if (gatewayUrl != null) {
            return gatewayUrl;
        }
        return workOrderId.startsWith("WO-") ? monolithUrl : serviceUrl;
    }

    /** One pass over the list; returns the number of transitions done. */
    int round() {
        var listed = http.get((gatewayUrl != null ? gatewayUrl : serviceUrl) + "/api/work-orders");
        if (listed.status() != 200) {
            System.err.println("list: " + listed.status());
            return 0;
        }
        var done = 0;
        for (var item : parse(listed.body())) {
            var action =
                    switch (item.status()) {
                        case "PLANNED" -> "start";
                        case "IN_PROGRESS" -> "complete";
                        default -> null;
                    };
            if (action == null) {
                continue;
            }
            try {
                var response = http.post(baseFor(item.id()) + "/api/work-orders/" + item.id() + "/" + action, null);
                if (response.status() == 200) {
                    done++;
                } else if (response.status() != 409) {
                    System.err.println(item.id() + " " + action + ": " + response.status());
                }
            } catch (IllegalStateException e) {
                System.err.println(item.id() + " " + action + ": " + e.getMessage());
            }
        }
        return done;
    }

    static List<Item> parse(String json) {
        var items = new ArrayList<Item>();
        var objects = OBJECT.matcher(json == null ? "" : json);
        while (objects.find()) {
            var id = ID.matcher(objects.group());
            var status = STATUS.matcher(objects.group());
            if (id.find() && status.find()) {
                items.add(new Item(id.group(1), status.group(1)));
            }
        }
        return items;
    }
}

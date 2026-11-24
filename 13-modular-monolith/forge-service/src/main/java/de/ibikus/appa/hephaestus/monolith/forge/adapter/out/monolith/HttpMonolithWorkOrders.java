package de.ibikus.appa.hephaestus.monolith.forge.adapter.out.monolith;

import de.ibikus.appa.hephaestus.monolith.forge.application.ListedWorkOrder;
import de.ibikus.appa.hephaestus.monolith.forge.application.MonolithWorkOrders;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@code GET ${MONOLITH_URL}/internal/work-orders}: the monolith's open work orders, in the service's words. */
@Component
class HttpMonolithWorkOrders implements MonolithWorkOrders {

    /** The monolith's JSON, as it is today. */
    record MonolithWorkOrder(String id, UUID orderId, String material, Integer quantity, String status) {}

    private final RestClient http;
    private final boolean configured;

    HttpMonolithWorkOrders(@Value("${monolith.url}") String url, @Value("${monolith.timeout}") Duration timeout) {
        this.configured = url != null && !url.isBlank();
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.http = RestClient.builder()
                .baseUrl(configured ? url : "http://unconfigured")
                .requestFactory(factory)
                .build();
    }

    @Override
    public boolean configured() {
        return configured;
    }

    @Override
    public List<ListedWorkOrder> open() {
        try {
            var items = http.get()
                    .uri("/internal/work-orders")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<MonolithWorkOrder>>() {});
            return items == null
                    ? List.of()
                    : items.stream()
                            .map(wo -> new ListedWorkOrder(
                                    wo.id(),
                                    wo.orderId(),
                                    wo.material(),
                                    wo.quantity() == null ? 0 : wo.quantity(),
                                    wo.status(),
                                    "MONOLITH"))
                            .toList();
        } catch (RestClientException e) {
            throw new MonolithUnavailable("monolith work orders unavailable", e);
        }
    }
}

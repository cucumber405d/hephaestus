package de.ibikus.appa.hephaestus.es.ordering.ordering.internal;

import de.ibikus.appa.hephaestus.es.ordering.ordering.CustomerOrder;
import de.ibikus.appa.hephaestus.es.ordering.ordering.OrderRequest;
import de.ibikus.appa.hephaestus.es.ordering.ordering.Orders;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    private final Orders orders;

    OrderController(Orders orders) {
        this.orders = orders;
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, UUID> place(@RequestBody OrderRequest request) {
        return Map.of("id", orders.place(request));
    }

    @GetMapping("/api/orders/{id}")
    CustomerOrder get(@PathVariable UUID id) {
        return orders.get(id);
    }
}

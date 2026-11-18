package de.ibikus.appa.hephaestus.resilience.inventory.adapter.in.web;

import de.ibikus.appa.hephaestus.resilience.inventory.application.port.in.StockQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public stock API in two versions, selected by the API-Version header (Spring Framework 7 API versioning). */
@RestController
@RequestMapping("/api/stock")
class StockController {

    private final StockQuery stock;

    StockController(StockQuery stock) {
        this.stock = stock;
    }

    /** Version 1 (deprecated): only what can be ordered. */
    @GetMapping(path = "/{sku}", version = "1")
    StockV1 v1(@PathVariable String sku) {
        var item = stock.get(sku);
        return new StockV1(item.sku(), item.available());
    }

    /** Version 2: the full picture. */
    @GetMapping(path = "/{sku}", version = "2")
    StockV2 v2(@PathVariable String sku) {
        var item = stock.get(sku);
        return new StockV2(item.sku(), item.name(), item.onHand(), item.reserved(), item.available());
    }

    record StockV1(String sku, int available) {}

    record StockV2(String sku, String name, int onHand, int reserved, int available) {}
}

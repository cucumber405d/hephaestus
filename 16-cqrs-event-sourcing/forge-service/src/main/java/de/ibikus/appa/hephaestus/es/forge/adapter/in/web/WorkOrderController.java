package de.ibikus.appa.hephaestus.es.forge.adapter.in.web;

import de.ibikus.appa.hephaestus.es.forge.application.BoardEntry;
import de.ibikus.appa.hephaestus.es.forge.application.StockLedger;
import de.ibikus.appa.hephaestus.es.forge.application.WorkOrders;
import de.ibikus.appa.hephaestus.es.forge.application.WorkshopBoard;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The smith's API: the list is the workshop board (read side), start/complete are commands (write side). */
@RestController
class WorkOrderController {

    private final WorkOrders workOrders;
    private final WorkshopBoard board;
    private final StockLedger ledger;

    WorkOrderController(WorkOrders workOrders, WorkshopBoard board, StockLedger ledger) {
        this.workOrders = workOrders;
        this.board = board;
        this.ledger = ledger;
    }

    @GetMapping("/api/work-orders")
    List<BoardEntry> list(@RequestParam Optional<String> status) {
        return board.list(status);
    }

    @PostMapping("/api/work-orders/{id}/start")
    void start(@PathVariable String id, @RequestParam String smith) {
        workOrders.start(id, smith);
    }

    @PostMapping("/api/work-orders/{id}/complete")
    void complete(@PathVariable String id) {
        workOrders.complete(id);
    }

    /** Without {@code at}: the stock-ledger projection; with {@code at}: a fold of the journal up to that moment. */
    @GetMapping("/api/stock/{material}")
    Map<String, Object> stock(@PathVariable String material, @RequestParam Optional<Instant> at) {
        int onHand = at.map(moment -> workOrders.stockAt(material, moment))
                .orElseGet(() -> ledger.onHand(material).orElse(0));
        return Map.of(
                "material",
                material,
                "onHand",
                onHand,
                "at",
                at.map(Instant::toString).orElse("now"));
    }
}

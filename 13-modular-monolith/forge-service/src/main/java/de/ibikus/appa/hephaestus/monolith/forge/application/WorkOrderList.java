package de.ibikus.appa.hephaestus.monolith.forge.application;

import de.ibikus.appa.hephaestus.monolith.forge.domain.WorkOrder.Status;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Strangler Fig facade for the smith's list: the service's work orders plus the monolith's open ones. When the
 * monolith does not answer, the list is the service's alone and says so ({@code partial}) — never an error page.
 */
@Service
public class WorkOrderList {

    public record Listing(List<ListedWorkOrder> items, boolean partial) {}

    private final WorkOrders workOrders;
    private final MonolithWorkOrders monolith;

    public WorkOrderList(WorkOrders workOrders, MonolithWorkOrders monolith) {
        this.workOrders = workOrders;
        this.monolith = monolith;
    }

    public Listing list(Status status) {
        var items = new ArrayList<ListedWorkOrder>();
        for (var wo : workOrders.list(status)) {
            items.add(new ListedWorkOrder(
                    wo.id(),
                    wo.orderId(),
                    wo.material(),
                    wo.quantity(),
                    wo.status().name(),
                    "SERVICE"));
        }
        if (!monolith.configured()) {
            return new Listing(items, false);
        }
        try {
            monolith.open().stream()
                    .filter(wo -> status == null || status.name().equals(wo.status()))
                    .forEach(items::add);
            return new Listing(items, false);
        } catch (MonolithWorkOrders.MonolithUnavailable e) {
            return new Listing(items, true);
        }
    }
}

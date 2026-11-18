package de.ibikus.appa.hephaestus.eventskafka.ordering.application.fakes;

import de.ibikus.appa.hephaestus.eventskafka.ordering.application.port.out.OrderEvents;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.EventPublishException;
import de.ibikus.appa.hephaestus.eventskafka.ordering.domain.Order;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records published events; can be switched to "broker down". */
public class FakeOrderEvents implements OrderEvents {

    private final List<Order> published = new CopyOnWriteArrayList<>();
    private volatile boolean down;

    public void brokerDown() {
        down = true;
    }

    public void brokerUp() {
        down = false;
    }

    public List<Order> published() {
        return published;
    }

    @Override
    public void orderPlaced(Order order) {
        if (down) {
            throw new EventPublishException("broker down", new RuntimeException("timeout"));
        }
        published.add(order);
    }
}

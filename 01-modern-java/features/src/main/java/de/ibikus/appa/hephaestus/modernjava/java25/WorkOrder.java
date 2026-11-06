package de.ibikus.appa.hephaestus.modernjava.java25;

import java.util.concurrent.atomic.AtomicInteger;

/** Base class with a side effect in its constructor (it counts created orders). */
public class WorkOrder {

    public static final AtomicInteger CREATED = new AtomicInteger();

    private final int quantity;

    public WorkOrder(int quantity) {
        this.quantity = quantity;
        CREATED.incrementAndGet();
    }

    public int quantity() {
        return quantity;
    }
}

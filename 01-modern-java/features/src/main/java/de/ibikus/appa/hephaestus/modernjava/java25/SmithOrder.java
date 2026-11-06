package de.ibikus.appa.hephaestus.modernjava.java25;

/**
 * Java 25 flexible constructor bodies (JEP 513; preview in 22-24): statements may run before {@code super(...)},
 * so invalid arguments are rejected before the superclass constructor and its side effects run. The prologue may
 * not touch {@code this}.
 */
public class SmithOrder extends WorkOrder {

    public SmithOrder(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
        super(quantity);
    }
}

package de.ibikus.appa.hephaestus.contracts.forge.domain;

/** A quantity below 1. */
public class InvalidQuantity extends RuntimeException {

    public InvalidQuantity(int quantity) {
        super("invalid quantity: " + quantity);
    }
}

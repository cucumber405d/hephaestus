package de.ibikus.appa.hephaestus.es.forge.domain.material;

/** Taking more than is on hand: the invariant "stock never below zero" of the material stream. */
public class InsufficientStock extends RuntimeException {

    public InsufficientStock(int needed, int onHand) {
        super("need " + needed + ", on hand " + onHand);
    }
}

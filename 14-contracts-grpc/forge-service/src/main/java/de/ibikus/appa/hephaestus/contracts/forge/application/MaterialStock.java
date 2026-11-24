package de.ibikus.appa.hephaestus.contracts.forge.application;

/** Port: the service's share of the material stock. */
public interface MaterialStock {

    /** Locks the material row for the rest of the transaction. */
    int lockOnHand(String code);

    void add(String code, int quantity);

    void take(String code, int quantity);
}

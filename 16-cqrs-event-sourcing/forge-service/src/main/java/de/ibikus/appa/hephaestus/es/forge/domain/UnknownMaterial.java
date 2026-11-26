package de.ibikus.appa.hephaestus.es.forge.domain;

/** The material is not on Forge's price list. */
public class UnknownMaterial extends RuntimeException {

    public UnknownMaterial(String material) {
        super("unknown material: " + material);
    }
}

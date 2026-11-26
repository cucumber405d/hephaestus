package de.ibikus.appa.hephaestus.es.forge.domain.material;

public record MaterialState(int onHand) {

    public static final MaterialState INITIAL = new MaterialState(0);
}

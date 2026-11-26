package de.ibikus.appa.hephaestus.es.forge.application;

import java.util.List;
import java.util.Optional;

/** The query side of the workshop-board projection: eventually consistent with the journal. */
public interface WorkshopBoard {

    List<BoardEntry> list(Optional<String> status);

    /** Work orders still waiting for material (the supplier retry walks them). */
    List<String> waitingForMaterial();
}

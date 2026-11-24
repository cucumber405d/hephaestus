package de.ibikus.appa.hephaestus.contracts.forge.application;

import de.ibikus.appa.hephaestus.contracts.forge.domain.Quote;
import java.util.Optional;

/** Port: the quotes Forge gave. */
public interface QuoteStore {

    void save(Quote quote);

    Optional<Quote> find(String id);
}

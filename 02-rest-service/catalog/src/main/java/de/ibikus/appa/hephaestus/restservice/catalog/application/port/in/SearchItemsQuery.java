package de.ibikus.appa.hephaestus.restservice.catalog.application.port.in;

import de.ibikus.appa.hephaestus.restservice.catalog.domain.Item;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemSearchCriteria;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.Page;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.PageRequest;

/** Driving port: item search with filters, sorting and offset pagination. */
public interface SearchItemsQuery {

    Page<Item> search(ItemSearchCriteria criteria, PageRequest pageRequest);
}

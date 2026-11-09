package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web.api.ItemsApi;
import de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web.api.model.ItemDto;
import de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web.api.model.ItemInputDto;
import de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web.api.model.ItemPageDto;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ManageItemsUseCase;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.SearchItemsQuery;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemId;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.ItemSearchCriteria;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.PageRequest;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.SortOrder;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Implements the generated contract interface; mapping and HTTP semantics only. */
@RestController
class ItemsController implements ItemsApi {

    private final ManageItemsUseCase items;
    private final SearchItemsQuery search;
    private final WebMapper mapper;

    ItemsController(ManageItemsUseCase items, SearchItemsQuery search, WebMapper mapper) {
        this.items = items;
        this.search = search;
        this.mapper = mapper;
    }

    /** The Idempotency-Key itself is handled by IdempotencyFilter before this method runs. */
    @Override
    public ResponseEntity<ItemDto> createItem(String idempotencyKey, ItemInputDto itemInputDto) {
        var item = items.create(mapper.toNewItem(itemInputDto));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(item.id().value())
                .toUri();
        return ResponseEntity.created(location).eTag(ETags.of(item.version())).body(mapper.toDto(item));
    }

    @Override
    public ResponseEntity<ItemDto> getItem(UUID itemId) {
        var item = items.get(new ItemId(itemId));
        return ResponseEntity.ok().eTag(ETags.of(item.version())).body(mapper.toDto(item));
    }

    @Override
    public ResponseEntity<ItemPageDto> searchItems(
            UUID category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String q,
            List<String> sort,
            Integer page,
            Integer size) {
        var orders = sort == null
                ? List.<SortOrder>of()
                : sort.stream()
                        .flatMap(value -> Arrays.stream(value.split(",")))
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .map(SortOrder::parse)
                        .toList();
        var criteria = new ItemSearchCriteria(
                category == null ? null : new CategoryId(category), minPrice, maxPrice, q, orders);
        return ResponseEntity.ok(mapper.toDto(search.search(criteria, new PageRequest(page, size))));
    }

    @Override
    public ResponseEntity<ItemDto> replaceItem(UUID itemId, ItemInputDto itemInputDto, String ifMatch) {
        var item = items.replace(new ItemId(itemId), requireIfMatch(ifMatch), mapper.toNewItem(itemInputDto));
        return ResponseEntity.ok().eTag(ETags.of(item.version())).body(mapper.toDto(item));
    }

    @Override
    public ResponseEntity<ItemDto> patchItem(UUID itemId, Map<String, Object> requestBody, String ifMatch) {
        long expectedVersion = requireIfMatch(ifMatch);
        var item = items.patch(new ItemId(itemId), expectedVersion, MergePatchParser.parse(requestBody));
        return ResponseEntity.ok().eTag(ETags.of(item.version())).body(mapper.toDto(item));
    }

    @Override
    public ResponseEntity<Void> deleteItem(UUID itemId, String ifMatch) {
        items.delete(new ItemId(itemId), requireIfMatch(ifMatch));
        return ResponseEntity.noContent().build();
    }

    /** If-Match is optional in the contract only so that its absence can be answered with 428 instead of 400. */
    private static long requireIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match header is required for this operation");
        }
        return ETags.parseIfMatch(ifMatch);
    }
}

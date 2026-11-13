package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.containersci.catalog.application.port.in.ManageItemsUseCase;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.CategoryId;
import de.ibikus.appa.hephaestus.containersci.catalog.domain.ItemId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/items")
class ItemsController {

    private final ManageItemsUseCase items;

    ItemsController(ManageItemsUseCase items) {
        this.items = items;
    }

    @PostMapping
    ResponseEntity<ItemResponse> create(@RequestBody ItemRequest request) {
        var item = items.create(WebMapper.toNewItem(request));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(item.id().value())
                .toUri();
        return ResponseEntity.created(location).body(WebMapper.toResponse(item));
    }

    @GetMapping("/{id}")
    ItemResponse get(@PathVariable UUID id) {
        return WebMapper.toResponse(items.get(new ItemId(id)));
    }

    @GetMapping
    List<ItemResponse> list(@RequestParam(required = false) UUID category) {
        return items.list(Optional.ofNullable(category).map(CategoryId::new)).stream()
                .map(WebMapper::toResponse)
                .toList();
    }
}

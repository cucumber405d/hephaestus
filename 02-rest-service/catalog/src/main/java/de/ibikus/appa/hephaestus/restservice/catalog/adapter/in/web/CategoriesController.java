package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web.api.CategoriesApi;
import de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web.api.model.CategoryDto;
import de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web.api.model.NewCategoryDto;
import de.ibikus.appa.hephaestus.restservice.catalog.application.port.in.ManageCategoriesUseCase;
import de.ibikus.appa.hephaestus.restservice.catalog.domain.CategoryId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Implements the generated contract interface; mapping and HTTP semantics only, no business rules. */
@RestController
class CategoriesController implements CategoriesApi {

    private final ManageCategoriesUseCase categories;
    private final WebMapper mapper;

    CategoriesController(ManageCategoriesUseCase categories, WebMapper mapper) {
        this.categories = categories;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<CategoryDto> createCategory(NewCategoryDto newCategoryDto) {
        var category = categories.create(newCategoryDto.getName());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(category.id().value())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(ETags.of(category.version()))
                .body(mapper.toDto(category));
    }

    @Override
    public ResponseEntity<List<CategoryDto>> listCategories() {
        return ResponseEntity.ok(categories.list().stream().map(mapper::toDto).toList());
    }

    @Override
    public ResponseEntity<CategoryDto> getCategory(UUID categoryId) {
        var category = categories.get(new CategoryId(categoryId));
        return ResponseEntity.ok().eTag(ETags.of(category.version())).body(mapper.toDto(category));
    }

    @Override
    public ResponseEntity<Void> deleteCategory(UUID categoryId) {
        categories.delete(new CategoryId(categoryId));
        return ResponseEntity.noContent().build();
    }
}

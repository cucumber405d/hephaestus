package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import de.ibikus.appa.hephaestus.containersci.catalog.application.port.in.ListCategoriesUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
class CategoriesController {

    private final ListCategoriesUseCase categories;

    CategoriesController(ListCategoriesUseCase categories) {
        this.categories = categories;
    }

    @GetMapping
    List<CategoryResponse> list() {
        return categories.list().stream().map(WebMapper::toResponse).toList();
    }
}

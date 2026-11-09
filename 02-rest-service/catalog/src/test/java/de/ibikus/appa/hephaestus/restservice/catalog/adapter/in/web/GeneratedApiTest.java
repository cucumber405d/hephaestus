package de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The contract parses cleanly and the generator produced every operation the spec defines. */
class GeneratedApiTest {

    private static final String API = "de.ibikus.appa.hephaestus.restservice.catalog.adapter.in.web.api.";

    @Test
    void contractParsesWithoutMessages() {
        var options = new ParseOptions();
        options.setResolve(true);

        var result = new OpenAPIV3Parser()
                .readLocation(
                        Path.of("src/main/resources/static/catalog-api.yaml")
                                .toUri()
                                .toString(),
                        null,
                        options);

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI().getPaths()).containsKeys("/api/categories", "/api/items");
    }

    @Test
    void itemsApiHasAllOperations() {
        assertThat(methodNames("ItemsApi"))
                .contains("createItem", "searchItems", "getItem", "replaceItem", "patchItem", "deleteItem");
    }

    @Test
    void categoriesApiHasAllOperations() {
        assertThat(methodNames("CategoriesApi"))
                .contains("createCategory", "listCategories", "getCategory", "deleteCategory");
    }

    /** Reflection instead of a direct reference: a missing interface is an assertion failure, not a compile error. */
    private static List<String> methodNames(String simpleName) {
        try {
            return Arrays.stream(Class.forName(API + simpleName).getMethods())
                    .map(Method::getName)
                    .toList();
        } catch (ClassNotFoundException e) {
            return List.of();
        }
    }
}

package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(CategoriesController.class)
@Import({ProblemDetailsAdvice.class, WebSliceFakes.class})
class CategoriesControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void listsCategoriesByName() {
        var result = mvc.get().uri("/api/categories").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Armor", "Tools", "Weapons");
        assertThat(result).bodyJson().extractingPath("$[0].id").isEqualTo("01900000-0000-7000-8000-000000000001");
    }
}

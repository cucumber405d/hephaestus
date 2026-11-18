package de.ibikus.appa.hephaestus.security.inventory.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(StockController.class)
@Import({ProblemDetailsAdvice.class, ApiVersioningConfiguration.class, SecurityConfiguration.class, WebSliceFakes.class
})
class StockControllerTest {

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvcTester mvc;

    @Test
    void stockWithoutTokenIs401() {
        assertThat(mvc.get().uri("/api/stock/ANVIL-01").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anyAuthenticatedCallerMayReadStock() {
        assertThat(mvc.get()
                        .uri("/api/stock/ANVIL-01")
                        .with(TestTokens.withRoles("bob", "smith"))
                        .exchange())
                .hasStatus(HttpStatus.OK);
    }

    @Test
    void versionOneIsTheDefaultAndShowsOnlyAvailable() throws Exception {
        var result =
                mvc.get().uri("/api/stock/ANVIL-01").with(TestTokens.customer()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(10);
        assertThat(result).bodyJson().extractingPath("$.sku").isEqualTo("ANVIL-01");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("onHand");
    }

    @Test
    void versionOneIsMarkedDeprecated() {
        var result = mvc.get()
                .uri("/api/stock/ANVIL-01")
                .header("API-Version", "1")
                .with(TestTokens.customer())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getHeader("Deprecation")).isNotBlank();
        assertThat(result.getResponse().getHeader("Sunset")).isNotBlank();
        assertThat(result.getResponse().getHeader("Link")).contains("deprecation");
    }

    @Test
    void versionTwoShowsTheFullPictureWithoutDeprecation() {
        var result = mvc.get()
                .uri("/api/stock/ANVIL-01")
                .header("API-Version", "2")
                .with(TestTokens.customer())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.onHand").isEqualTo(10);
        assertThat(result).bodyJson().extractingPath("$.reserved").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Anvil");
        assertThat(result.getResponse().getHeader("Deprecation")).isNull();
    }

    @Test
    void unsupportedVersionIs400() {
        var result = mvc.get()
                .uri("/api/stock/ANVIL-01")
                .header("API-Version", "3")
                .with(TestTokens.customer())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownSkuIs404() {
        var result = mvc.get()
                .uri("/api/stock/NOPE-01")
                .header("API-Version", "2")
                .with(TestTokens.customer())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("https://hephaestus.ibikus.de/problems/not-found");
    }
}

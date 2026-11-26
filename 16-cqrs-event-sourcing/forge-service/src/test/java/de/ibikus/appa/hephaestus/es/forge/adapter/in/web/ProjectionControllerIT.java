package de.ibikus.appa.hephaestus.es.forge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.es.forge.Containers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The replay endpoints: status of every projection, rebuild by name. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(Containers.class)
class ProjectionControllerIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void statusListsEveryProjection() {
        assertThat(mvc.get().uri("/admin/projections"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .contains("workshop-board", "smith-load", "stock-ledger");
    }

    @Test
    void rebuildKnownIsAccepted() {
        assertThat(mvc.post().uri("/admin/projections/{name}/rebuild", "stock-ledger"))
                .hasStatus(202);
    }

    @Test
    void rebuildUnknownIs404() {
        assertThat(mvc.post().uri("/admin/projections/{name}/rebuild", "nope")).hasStatus(404);
    }

    @Test
    void rebuildPublisherIs400() {
        assertThat(mvc.post().uri("/admin/projections/{name}/rebuild", "forge-events-publisher"))
                .hasStatus(400);
    }
}

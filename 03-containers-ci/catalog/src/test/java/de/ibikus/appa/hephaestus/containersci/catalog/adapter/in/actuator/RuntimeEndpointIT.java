package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.containersci.catalog.PostgresTestcontainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainers.class)
class RuntimeEndpointIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void exposesRuntime() {
        var result = mvc.get().uri("/actuator/runtime").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.availableProcessors")
                .isEqualTo(Runtime.getRuntime().availableProcessors());
        assertThat(result).bodyJson().extractingPath("$.gc").asArray().isNotEmpty();
        assertThat(result).bodyJson().extractingPath("$.javaVersion").asString().startsWith("25");
        assertThat(result).bodyJson().extractingPath("$.maxHeapBytes").isNotNull();
    }
}

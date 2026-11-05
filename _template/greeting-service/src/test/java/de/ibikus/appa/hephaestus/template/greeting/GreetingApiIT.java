package de.ibikus.appa.hephaestus.template.greeting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GreetingApiIT {

    @Value("${local.server.port}")
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void greetsOverHttp() {
        ResponseEntity<String> response =
                client().get().uri("/api/greetings/Hephaestus").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("Hello, Hephaestus!");
    }

    @Test
    void reportsReadiness() {
        ResponseEntity<String> response =
                client().get().uri("/actuator/health/readiness").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}

package de.ibikus.appa.hephaestus.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.json.JsonMapper;

/**
 * The registry decides compatibility, as in CI and in production: the released 1.0.0 schemas are the first version
 * of each artifact, the rule is BACKWARD, and the current schemas must be accepted as the next version. Scenario 3
 * and 4 of scripts/breaking-change.sh make {@link #currentSchemasAreBackwardCompatibleWithReleased} fail.
 */
class SchemaCompatibilityIT {

    private static final Map<String, String> ARTIFACTS =
            Map.of("cdc.forge.requests-value", "work_order_request.avsc", "cdc.forge.events-value", "forge_event.avsc");

    private static final GenericContainer<?> REGISTRY = new GenericContainer<>(
                    System.getProperty("apicurio.image", "apicurio/apicurio-registry:3.3.5"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/apis/registry/v3/system/info").forStatusCode(200));

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeAll
    static void start() {
        REGISTRY.start();
    }

    @AfterAll
    static void stop() {
        REGISTRY.stop();
    }

    @Test
    void currentSchemasAreBackwardCompatibleWithReleased() throws Exception {
        for (var artifact : ARTIFACTS.entrySet()) {
            var id = artifact.getKey() + "-" + UUID.randomUUID();
            createWithBackwardRule(id, read("released/1.0.0/avro/" + artifact.getValue()));

            var next = addVersion(id, read("src/main/avro/" + artifact.getValue()));

            assertThat(next.statusCode())
                    .as("%s: %s", artifact.getValue(), next.body())
                    .isEqualTo(200);
        }
    }

    @Test
    void requiredFieldWithoutDefaultIsRejected() throws Exception {
        var id = "cdc.forge.requests-value-" + UUID.randomUUID();
        var current = read("src/main/avro/work_order_request.avsc");
        createWithBackwardRule(id, current);
        var withRequiredField =
                current.replace("\"fields\": [", "\"fields\": [\n    {\"name\": \"priority\", \"type\": \"int\"},");

        var next = addVersion(id, withRequiredField);

        // Apicurio 3.3 answers 400 with a RuleViolationException naming the field (older versions: 409)
        assertThat(next.statusCode()).as(next.body()).isIn(400, 409);
        assertThat(next.body()).contains("RuleViolationException").contains("priority");
    }

    private static void createWithBackwardRule(String id, String schema) throws Exception {
        var created = post(
                "/groups/default/artifacts",
                Map.of("artifactId", id, "artifactType", "AVRO", "firstVersion", Map.of("content", content(schema))));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        var rule = post(
                "/groups/default/artifacts/" + id + "/rules",
                Map.of("ruleType", "COMPATIBILITY", "config", "BACKWARD"));
        assertThat(rule.statusCode()).as(rule.body()).isBetween(200, 204);
    }

    private static HttpResponse<String> addVersion(String id, String schema) throws Exception {
        return post("/groups/default/artifacts/" + id + "/versions", Map.of("content", content(schema)));
    }

    private static Map<String, String> content(String schema) {
        return Map.of("content", schema, "contentType", "application/json");
    }

    private static HttpResponse<String> post(String path, Object body) throws Exception {
        var uri = URI.create(
                "http://" + REGISTRY.getHost() + ":" + REGISTRY.getMappedPort(8080) + "/apis/registry/v3" + path);
        var request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}

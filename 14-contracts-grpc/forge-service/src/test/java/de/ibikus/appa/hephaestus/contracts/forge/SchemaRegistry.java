package de.ibikus.appa.hephaestus.contracts.forge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Registers the contract schemas over Apicurio's v3 REST API, as scripts/register-schemas.sh does in compose. */
public final class SchemaRegistry {

    /** The contracts module, seen from this module's directory (the test's working directory). */
    public static final Path CONTRACTS = Path.of("../contracts");

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SchemaRegistry() {}

    public static void registerContracts(String baseUrl) {
        for (var version : new String[] {"released/1.0.0/avro", "src/main/avro"}) {
            register(baseUrl, "forge.requests-value", CONTRACTS.resolve(version + "/work_order_request.avsc"));
            register(baseUrl, "forge.events-value", CONTRACTS.resolve(version + "/forge_event.avsc"));
        }
    }

    static void register(String baseUrl, String artifactId, Path schema) {
        var api = baseUrl + "/apis/registry/v3/groups/default/artifacts";
        var content = Map.of("content", read(schema), "contentType", "application/json");
        var exists = send(HttpRequest.newBuilder(URI.create(api + "/" + artifactId)).GET());
        var created = exists.statusCode() == 404
                ? post(api, Map.of("artifactId", artifactId, "artifactType", "AVRO", "firstVersion", Map.of("content", content)))
                : post(api + "/" + artifactId + "/versions", Map.of("content", content));
        check(created, artifactId);
        if (exists.statusCode() == 404) {
            check(post(api + "/" + artifactId + "/rules", Map.of("ruleType", "COMPATIBILITY", "config", "BACKWARD")), artifactId);
        }
    }

    private static HttpResponse<String> post(String uri, Object body) {
        return send(HttpRequest.newBuilder(URI.create(uri))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void check(HttpResponse<String> response, String artifactId) {
        if (response.statusCode() >= 300) {
            throw new IllegalStateException(artifactId + ": HTTP " + response.statusCode() + " " + response.body());
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

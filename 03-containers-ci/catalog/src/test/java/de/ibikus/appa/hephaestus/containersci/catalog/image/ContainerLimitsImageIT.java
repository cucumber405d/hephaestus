package de.ibikus.appa.hephaestus.containersci.catalog.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Container awareness: what the JVM in the image sees under docker CPU and memory limits. Jib and the Dockerfiles
 * leave sizing to the JVM (MaxRAMPercentage=75); Buildpacks' memory calculator sets its own flags, so for it only
 * the visible limit is asserted and the numbers are logged for the learning notes.
 */
class ContainerLimitsImageIT {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerLimitsImageIT.class);

    static final long MIB_768 = 805_306_368L;
    static final long GIB_2 = 2_147_483_648L;
    private static final long NANO_CPUS_PER_CPU = 1_000_000_000L;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ImageFlavor FLAVOR = ImageFlavor.current();

    private static final Network NETWORK = Network.newNetwork();
    private static final PostgreSQLContainer POSTGRES = CatalogContainers.postgres(NETWORK);

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
        NETWORK.close();
    }

    @Test
    void oneCpu() throws Exception {
        var runtime = runtimeUnder(1.0, MIB_768, Map.of());

        assertThat(runtime.get("containerMemoryLimitBytes").asLong()).isEqualTo(MIB_768);
        if (!FLAVOR.jvmFlagsFromImage()) {
            LOG.info("{} under 1 CPU / 768 MiB: {}", FLAVOR, runtime);
            return;
        }
        assertThat(runtime.get("availableProcessors").asInt()).isEqualTo(1);
        assertThat(runtime.get("maxHeapBytes").asLong()).isBetween((long) (0.65 * MIB_768), (long) (0.85 * MIB_768));
        // fewer than 2 CPUs or less than ~1792 MiB: not a "server-class machine", the JVM picks SerialGC
        assertThat(gcNames(runtime)).contains("MarkSweepCompact");
    }

    @Test
    void halfCpuRoundsUp() throws Exception {
        assumeTrue(FLAVOR.jvmFlagsFromImage(), "Buildpacks sets ActiveProcessorCount itself");

        var runtime = runtimeUnder(0.5, MIB_768, Map.of());

        assertThat(runtime.get("availableProcessors").asInt()).isEqualTo(1);
    }

    @Test
    void activeProcessorCountOverrides() throws Exception {
        assumeTrue(FLAVOR.jvmFlagsFromImage(), "Buildpacks sets ActiveProcessorCount itself");

        var runtime = runtimeUnder(1.0, MIB_768, Map.of("JAVA_TOOL_OPTIONS", "-XX:ActiveProcessorCount=3"));

        assertThat(runtime.get("availableProcessors").asInt()).isEqualTo(3);
    }

    @Test
    void twoCpusTwoGigabytesSelectG1() throws Exception {
        assumeTrue(FLAVOR.jvmFlagsFromImage(), "Buildpacks GC choice is recorded in the notes, not asserted");

        var runtime = runtimeUnder(2.0, GIB_2, Map.of());

        assertThat(gcNames(runtime)).contains("G1 Young Generation");
    }

    private static JsonNode runtimeUnder(double cpus, long memoryBytes, Map<String, String> env) throws Exception {
        try (var catalog = CatalogContainers.catalog(NETWORK, CatalogContainers.image())
                .withEnv(env)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                        .withNanoCPUs((long) (cpus * NANO_CPUS_PER_CPU))
                        .withMemory(memoryBytes))) {
            catalog.start();
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(
                                    CatalogContainers.baseUrl(catalog, CatalogContainers.PORT) + "/actuator/runtime"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return JSON.readTree(response.body());
        }
    }

    private static java.util.List<String> gcNames(JsonNode runtime) {
        var names = new java.util.ArrayList<String>();
        // JsonNode is Iterable over array elements in both Jackson 2 and 3
        runtime.get("gc").forEach(node -> names.add(node.asString()));
        return names;
    }
}

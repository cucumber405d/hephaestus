package de.ibikus.appa.hephaestus.modernjava.java25;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Java 25 compact source files (JEP 512): the launcher runs scripts/Inventory.java directly, no class, no build. */
class InventoryScriptTest {

    @Test
    void scriptRunsWithPlainJavaLauncher() throws Exception {
        var java = ProcessHandle.current().info().command().orElseThrow();
        var script = Path.of("..", "scripts", "Inventory.java").toAbsolutePath().normalize();

        var process = new ProcessBuilder(java, script.toString())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("Hephaestus inventory: 3 materials");
    }
}

package de.ibikus.appa.hephaestus.scaling.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrderLogTest {

    @TempDir
    Path dir;

    @Test
    void concurrentAppendsKeepEveryLine() throws Exception {
        var log = new OrderLog(dir.resolve("orders.csv"));
        try (var pool = Executors.newFixedThreadPool(16)) {
            for (int t = 0; t < 16; t++) {
                var thread = t;
                pool.submit(() -> {
                    for (int i = 0; i < 1000; i++) {
                        log.append("order-" + thread + "-" + i, "choreography", "CONFIRMED");
                    }
                });
            }
        }
        log.close();

        var lines = Files.readAllLines(dir.resolve("orders.csv"));
        assertThat(lines).hasSize(16_000);
        assertThat(new HashSet<>(lines)).hasSize(16_000);
        assertThat(lines.getFirst()).matches("order-\\d+-\\d+,choreography,CONFIRMED");
        // \n on every platform: reconcile.sh compares the last field as is (a Windows \r broke every comparison)
        assertThat(Files.readString(dir.resolve("orders.csv"))).doesNotContain("\r");
    }

    @Test
    void anExistingFileIsReplaced() throws Exception {
        var file = dir.resolve("orders.csv");
        Files.writeString(file, "old,run,line\n");

        var log = new OrderLog(file);
        log.append("new", "orchestration", "CONFIRMED");
        log.close();

        assertThat(Files.readAllLines(file)).containsExactly("new,orchestration,CONFIRMED");
    }
}

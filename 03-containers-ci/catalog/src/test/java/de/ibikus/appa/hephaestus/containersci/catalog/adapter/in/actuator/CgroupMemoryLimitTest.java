package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CgroupMemoryLimitTest {

    @TempDir
    Path dir;

    @Test
    void parsesLimitWithTrailingNewline() {
        assertThat(CgroupMemoryLimit.parse("805306368\n")).hasValue(805306368L);
    }

    @Test
    void maxMeansNoLimit() {
        assertThat(CgroupMemoryLimit.parse("max\n")).isEmpty();
    }

    @Test
    void garbageMeansNoLimit() {
        assertThat(CgroupMemoryLimit.parse("")).isEmpty();
        assertThat(CgroupMemoryLimit.parse("abc")).isEmpty();
        assertThat(CgroupMemoryLimit.parse("-1")).isEmpty();
        assertThat(CgroupMemoryLimit.parse("99999999999999999999999")).isEmpty();
    }

    @Test
    void readsFile() throws Exception {
        var file = Files.writeString(dir.resolve("memory.max"), "536870912\n");

        assertThat(CgroupMemoryLimit.read(file)).hasValue(536870912L);
    }

    @Test
    void missingFileMeansNoLimit() {
        assertThat(CgroupMemoryLimit.read(dir.resolve("absent"))).isEmpty();
    }

    @Test
    void unreadablePathMeansNoLimit() {
        assertThat(CgroupMemoryLimit.read(dir)).isEmpty();
    }
}

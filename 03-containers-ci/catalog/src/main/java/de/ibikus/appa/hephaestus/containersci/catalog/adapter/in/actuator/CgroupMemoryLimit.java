package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.actuator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

/**
 * Reads the cgroup v2 memory limit ({@code /sys/fs/cgroup/memory.max}). The file holds a byte count or "max" (no
 * limit). Anything else, a missing file (cgroup v1, no container) or an I/O error means "unknown" and is never an
 * error: the endpoint is diagnostics, not a dependency.
 */
final class CgroupMemoryLimit {

    static final Path DEFAULT_FILE = Path.of("/sys/fs/cgroup/memory.max");

    private CgroupMemoryLimit() {}

    static OptionalLong parse(String content) {
        var value = content.strip();
        if (!value.matches("\\d{1,18}")) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(value));
    }

    static OptionalLong read(Path file) {
        try {
            return parse(Files.readString(file));
        } catch (IOException | SecurityException e) {
            return OptionalLong.empty();
        }
    }
}

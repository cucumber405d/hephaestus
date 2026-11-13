package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.actuator;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * {@code /actuator/runtime}: CPU count, heap limit, container memory limit and GC as the JVM sees them. Used by the
 * image tests to prove container awareness, and by humans to check a running container.
 */
@Component
@Endpoint(id = "runtime")
public class RuntimeEndpoint {

    private final Path memoryMaxFile;

    public RuntimeEndpoint() {
        this(CgroupMemoryLimit.DEFAULT_FILE);
    }

    RuntimeEndpoint(Path memoryMaxFile) {
        this.memoryMaxFile = memoryMaxFile;
    }

    @ReadOperation
    public RuntimeInfo runtime() {
        var limit = CgroupMemoryLimit.read(memoryMaxFile);
        return new RuntimeInfo(
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory(),
                limit.isPresent() ? limit.getAsLong() : null,
                ManagementFactory.getGarbageCollectorMXBeans().stream()
                        .map(GarbageCollectorMXBean::getName)
                        .toList(),
                Runtime.version().toString(),
                ManagementFactory.getRuntimeMXBean().getInputArguments());
    }
}

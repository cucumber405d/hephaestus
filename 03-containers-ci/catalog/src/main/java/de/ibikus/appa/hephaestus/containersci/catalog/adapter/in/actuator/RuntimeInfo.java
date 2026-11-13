package de.ibikus.appa.hephaestus.containersci.catalog.adapter.in.actuator;

import java.util.List;

/** What the JVM sees about its resources; containerMemoryLimitBytes is null when there is no (known) limit. */
public record RuntimeInfo(
        int availableProcessors,
        long maxHeapBytes,
        Long containerMemoryLimitBytes,
        List<String> gc,
        String javaVersion,
        List<String> inputArguments) {}

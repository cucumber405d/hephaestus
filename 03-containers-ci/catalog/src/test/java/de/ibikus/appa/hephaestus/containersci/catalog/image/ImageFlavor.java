package de.ibikus.appa.hephaestus.containersci.catalog.image;

import java.util.Locale;

/** How the image under test was built; selects the expectations that differ between build methods. */
enum ImageFlavor {
    BUILDPACKS,
    JIB,
    DOCKERFILE,
    DOCKERFILE_JLINK;

    /** Jib and the Dockerfiles set JAVA_TOOL_OPTIONS themselves; Buildpacks computes flags at start-up. */
    boolean jvmFlagsFromImage() {
        return this != BUILDPACKS;
    }

    /** From -Dcatalog.flavor (buildpacks, jib, dockerfile, dockerfile-jlink); default jib. */
    static ImageFlavor current() {
        var value = System.getProperty("catalog.flavor", "jib");
        return valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}

package de.ibikus.appa.hephaestus.modernjava.java24;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java 24 Class-File API (JEP 484; preview in 22 and 23): parse and generate {@code .class} files with a JDK
 * API instead of ASM, always in sync with the current class-file version.
 */
public final class ClassInspector {

    private ClassInspector() {}

    public static Set<String> methodNames(Class<?> type) throws IOException {
        var resource = type.getName().replace('.', '/') + ".class";
        try (var in = type.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Class file not found: " + resource);
            }
            var model = ClassFile.of().parse(in.readAllBytes());
            return model.methods().stream()
                    .map(method -> method.methodName().stringValue())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}

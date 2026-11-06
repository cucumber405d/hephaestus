package de.ibikus.appa.hephaestus.modernjava.java25;

import module java.base;

/**
 * Java 25 module import declarations (JEP 511; preview in 23 and 24): {@code import module java.base;} imports
 * every package that java.base exports, here java.util. Handy for scripts and teaching; explicit imports still win
 * on name clashes.
 */
public final class ModuleImportExample {

    private ModuleImportExample() {}

    public static List<String> sortedCodes(Map<String, ?> catalog) {
        return List.copyOf(new TreeSet<>(catalog.keySet()));
    }
}

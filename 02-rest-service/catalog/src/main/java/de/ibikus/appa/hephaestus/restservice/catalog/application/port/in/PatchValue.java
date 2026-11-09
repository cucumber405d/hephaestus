package de.ibikus.appa.hephaestus.restservice.catalog.application.port.in;

/** One field of a JSON Merge Patch: key absent (keep), explicit null (remove) or a new value (set). */
public sealed interface PatchValue<T> {

    record Absent<T>() implements PatchValue<T> {}

    record Remove<T>() implements PatchValue<T> {}

    record Set<T>(T value) implements PatchValue<T> {}

    static <T> PatchValue<T> absent() {
        return new Absent<>();
    }

    static <T> PatchValue<T> remove() {
        return new Remove<>();
    }

    static <T> PatchValue<T> set(T value) {
        return new Set<>(value);
    }
}

package de.ibikus.appa.hephaestus.modernjava.java10;

import java.util.List;

/** Java 10 {@code List.copyOf}: an unmodifiable copy, detached from the source list. */
public record OrderSnapshot(List<String> items) {

    public OrderSnapshot {
        items = List.copyOf(items);
    }

    public static OrderSnapshot of(List<String> items) {
        var copy = List.copyOf(items); // Java 10 local variable type inference
        return new OrderSnapshot(copy);
    }
}

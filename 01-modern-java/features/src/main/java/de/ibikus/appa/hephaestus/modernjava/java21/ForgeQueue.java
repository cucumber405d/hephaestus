package de.ibikus.appa.hephaestus.modernjava.java21;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

/**
 * Java 21 sequenced collections (JEP 431): {@code first/last}, {@code putFirst/putLast} and {@code reversed()} on
 * every ordered collection, instead of {@code iterator().next()} or index arithmetic.
 */
public final class ForgeQueue {

    private final SequencedMap<String, WorkItem> items = new LinkedHashMap<>();

    public void add(WorkItem item) {
        items.putLast(item.id(), item);
    }

    public void addUrgent(WorkItem item) {
        items.putFirst(item.id(), item);
    }

    public WorkItem first() {
        return items.firstEntry().getValue();
    }

    public WorkItem last() {
        return items.lastEntry().getValue();
    }

    public List<String> idsNewestFirst() {
        return List.copyOf(items.sequencedKeySet().reversed());
    }
}

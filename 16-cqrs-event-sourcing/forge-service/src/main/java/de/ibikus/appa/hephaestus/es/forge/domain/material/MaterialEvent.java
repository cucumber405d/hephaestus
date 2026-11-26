package de.ibikus.appa.hephaestus.es.forge.domain.material;

/** Movements of one material — the journal's events of stream type {@code Material}. */
public sealed interface MaterialEvent {

    record StockReceived(int quantity, Source source) implements MaterialEvent {

        public enum Source {
            /** The opening stock of a new installation (migration). */
            INITIAL,
            SUPPLIER
        }
    }

    record StockTaken(int quantity, String workOrderId) implements MaterialEvent {}
}

package de.ibikus.appa.hephaestus.es.forge.domain.material;

import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialCommand.Receive;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialCommand.Take;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockTaken;
import java.util.List;

/** The material aggregate: one stream per material, its version guards "never below zero" against concurrent takes. */
public final class MaterialDecider {

    private MaterialDecider() {}

    public static String streamId(String code) {
        return "material-" + code;
    }

    public static List<MaterialEvent> decide(MaterialCommand command, MaterialState state) {
        return switch (command) {
            case Receive r -> List.of(new StockReceived(r.quantity(), r.source()));
            case Take t -> {
                if (t.quantity() > state.onHand()) {
                    throw new InsufficientStock(t.quantity(), state.onHand());
                }
                yield List.of(new StockTaken(t.quantity(), t.workOrderId()));
            }
        };
    }

    public static MaterialState evolve(MaterialState state, MaterialEvent event) {
        return switch (event) {
            case StockReceived r -> new MaterialState(state.onHand() + r.quantity());
            case StockTaken t -> new MaterialState(state.onHand() - t.quantity());
        };
    }

    public static MaterialState fold(List<? extends MaterialEvent> events) {
        var state = MaterialState.INITIAL;
        for (var event : events) {
            state = evolve(state, event);
        }
        return state;
    }
}

package de.ibikus.appa.hephaestus.es.forge.domain.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialCommand.Receive;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialCommand.Take;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockReceived.Source;
import de.ibikus.appa.hephaestus.es.forge.domain.material.MaterialEvent.StockTaken;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaterialDeciderTest {

    @Test
    void receiveThenTakeLeavesRest() {
        assertThat(MaterialDecider.streamId("IRON-01")).isEqualTo("material-IRON-01");
        var received = MaterialDecider.decide(new Receive(20, Source.INITIAL), MaterialState.INITIAL);
        assertThat(received).containsExactly(new StockReceived(20, Source.INITIAL));
        var taken = MaterialDecider.decide(new Take(5, "FW-1"), MaterialDecider.fold(received));
        assertThat(taken).containsExactly(new StockTaken(5, "FW-1"));
        var events = List.<MaterialEvent>of(received.getFirst(), taken.getFirst());
        assertThat(MaterialDecider.fold(events).onHand()).isEqualTo(15);
    }

    @Test
    void takeMoreThanOnHandThrowsInsufficientStock() {
        var state = MaterialDecider.fold(List.of(new StockReceived(3, Source.SUPPLIER)));
        assertThatThrownBy(() -> MaterialDecider.decide(new Take(5, "FW-1"), state))
                .isInstanceOf(InsufficientStock.class)
                .hasMessageContaining("5")
                .hasMessageContaining("3");
    }
}

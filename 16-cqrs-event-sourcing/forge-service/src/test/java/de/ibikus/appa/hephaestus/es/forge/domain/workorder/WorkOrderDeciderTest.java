package de.ibikus.appa.hephaestus.es.forge.domain.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Allocate;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Complete;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Fail;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Request;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderCommand.Start;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.MaterialAllocated;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderCompleted;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderFailed;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderRequested;
import de.ibikus.appa.hephaestus.es.forge.domain.workorder.WorkOrderEvent.WorkOrderStarted;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Given the stream's events, when a command arrives, then these events (or a refusal). */
class WorkOrderDeciderTest {

    static final UUID ORDER = UUID.fromString("7bc1745b-b821-4b81-a7ca-873cc4d5e7d6");
    static final String ID = "FW-" + ORDER;
    static final WorkOrderRequested REQUESTED = new WorkOrderRequested(ID, ORDER, "IRON-01", 5);

    @Test
    void requestOnNewGivesRequested() {
        assertThat(WorkOrderDecider.idFor(ORDER)).isEqualTo(ID);
        assertThat(decide(List.of(), new Request(ORDER, "IRON-01", 5))).containsExactly(REQUESTED);
    }

    @Test
    void requestAgainGivesNothing() {
        assertThat(decide(List.of(REQUESTED), new Request(ORDER, "IRON-01", 5))).isEmpty();
        assertThat(decide(List.of(REQUESTED, new MaterialAllocated(5), new WorkOrderStarted("anna"), new WorkOrderCompleted()),
                        new Request(ORDER, "IRON-01", 5)))
                .isEmpty();
    }

    @Test
    void allocateOnlyWhileWaiting() {
        assertThat(decide(List.of(REQUESTED), new Allocate())).containsExactly(new MaterialAllocated(5));
        assertThatThrownBy(() -> decide(List.of(REQUESTED, new MaterialAllocated(5)), new Allocate()))
                .isInstanceOf(WorkOrderRefused.class);
    }

    @Test
    void startNeedsAllocated() {
        assertThatThrownBy(() -> decide(List.of(REQUESTED), new Start("anna")))
                .isInstanceOf(WorkOrderRefused.class)
                .hasMessageContaining("cannot start");
        assertThat(decide(List.of(REQUESTED, new MaterialAllocated(5)), new Start("anna")))
                .containsExactly(new WorkOrderStarted("anna"));
    }

    @Test
    void startTwiceRefused() {
        assertThatThrownBy(() -> decide(
                        List.of(REQUESTED, new MaterialAllocated(5), new WorkOrderStarted("anna")), new Start("boris")))
                .isInstanceOf(WorkOrderRefused.class);
    }

    @Test
    void completeOnlyInProgress() {
        assertThatThrownBy(() -> decide(List.of(REQUESTED, new MaterialAllocated(5)), new Complete()))
                .isInstanceOf(WorkOrderRefused.class)
                .hasMessageContaining("cannot complete");
        var started = List.<WorkOrderEvent>of(REQUESTED, new MaterialAllocated(5), new WorkOrderStarted("anna"));
        assertThat(decide(started, new Complete())).containsExactly(new WorkOrderCompleted());
        var state = WorkOrderDecider.fold(started);
        assertThat(state.status()).isEqualTo(WorkOrderState.Status.IN_PROGRESS);
        assertThat(state.smith()).isEqualTo("anna");
    }

    @Test
    void failedIsTerminal() {
        var failed = List.<WorkOrderEvent>of(REQUESTED, new WorkOrderFailed("material not deliverable: IRON-01"));
        assertThat(decide(List.of(REQUESTED), new Fail("material not deliverable: IRON-01")))
                .containsExactly(new WorkOrderFailed("material not deliverable: IRON-01"));
        assertThat(WorkOrderDecider.fold(failed).failureReason()).isEqualTo("material not deliverable: IRON-01");
        assertThatThrownBy(() -> decide(failed, new Allocate())).isInstanceOf(WorkOrderRefused.class);
        assertThatThrownBy(() -> decide(failed, new Fail("again"))).isInstanceOf(WorkOrderRefused.class);
    }

    private static List<WorkOrderEvent> decide(List<WorkOrderEvent> given, WorkOrderCommand command) {
        return WorkOrderDecider.decide(command, WorkOrderDecider.fold(given));
    }
}

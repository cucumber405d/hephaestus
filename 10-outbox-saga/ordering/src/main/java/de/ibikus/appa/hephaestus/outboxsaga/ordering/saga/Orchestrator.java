package de.ibikus.appa.hephaestus.outboxsaga.ordering.saga;

import static de.ibikus.appa.hephaestus.outboxsaga.ordering.messaging.SagaMessage.*;

import java.util.List;

/**
 * The orchestration as pure decisions, no I/O: given the saga's state and a reply (or a timeout), what is the next
 * state, which commands go out, and is the order decided now. Steps: payment → stock → delivery. A failure at a
 * step compensates the steps before it, newest first. Replies that do not fit the current state (late, duplicate,
 * after a timeout) change nothing.
 */
public final class Orchestrator {

    public enum Step {
        PAYMENT,
        STOCK,
        DELIVERY
    }

    public enum State {
        RUNNING,
        COMPENSATING,
        CONFIRMED,
        REJECTED
    }

    public enum Outcome {
        CONFIRMED,
        REJECTED
    }

    public record Saga(Step step, State state, int pendingCompensations) {}

    /**
     * @param outcome set when this decision settles the order (null otherwise)
     * @param reason why the order was rejected
     */
    public record Decision(Saga next, List<String> commands, Outcome outcome, String reason) {

        static Decision ignore(Saga saga) {
            return new Decision(saga, List.of(), null, null);
        }

        public boolean ignored(Saga before) {
            return next.equals(before) && commands.isEmpty() && outcome == null;
        }
    }

    private Orchestrator() {}

    public static Saga start() {
        return new Saga(Step.PAYMENT, State.RUNNING, 0);
    }

    public static String firstCommand() {
        return RESERVE_PAYMENT;
    }

    public static Decision onReply(Saga saga, String reply, String replyReason) {
        if (saga.state() == State.COMPENSATING) {
            return compensationDone(saga, reply);
        }
        if (saga.state() != State.RUNNING) {
            return Decision.ignore(saga);
        }
        return switch (saga.step()) {
            case PAYMENT -> switch (reply) {
                case PAYMENT_RESERVED -> running(Step.STOCK, RESERVE_STOCK);
                case PAYMENT_DECLINED -> new Decision(
                        new Saga(Step.PAYMENT, State.REJECTED, 0), List.of(), Outcome.REJECTED, replyReason);
                default -> Decision.ignore(saga);
            };
            case STOCK -> switch (reply) {
                case STOCK_RESERVED -> running(Step.DELIVERY, SCHEDULE_DELIVERY);
                case STOCK_UNAVAILABLE -> compensate(Step.STOCK, List.of(REFUND_PAYMENT), replyReason);
                default -> Decision.ignore(saga);
            };
            case DELIVERY -> switch (reply) {
                case DELIVERY_SCHEDULED -> new Decision(
                        new Saga(Step.DELIVERY, State.CONFIRMED, 0), List.of(), Outcome.CONFIRMED, null);
                case DELIVERY_FAILED -> compensate(Step.DELIVERY, List.of(RELEASE_STOCK, REFUND_PAYMENT), replyReason);
                default -> Decision.ignore(saga);
            };
        };
    }

    /**
     * The current step did not answer in time. Its own action may still happen later, so it is compensated too:
     * the participants remember a compensation that came first and refuse the late action.
     */
    public static Decision onTimeout(Saga saga) {
        if (saga.state() != State.RUNNING) {
            return Decision.ignore(saga);
        }
        var reason = "timeout at step " + saga.step().name().toLowerCase();
        return switch (saga.step()) {
            case PAYMENT -> compensate(Step.PAYMENT, List.of(REFUND_PAYMENT), reason);
            case STOCK -> compensate(Step.STOCK, List.of(RELEASE_STOCK, REFUND_PAYMENT), reason);
            case DELIVERY -> compensate(Step.DELIVERY, List.of(CANCEL_DELIVERY, RELEASE_STOCK, REFUND_PAYMENT), reason);
        };
    }

    private static Decision running(Step step, String command) {
        return new Decision(new Saga(step, State.RUNNING, 0), List.of(command), null, null);
    }

    private static Decision compensate(Step step, List<String> commands, String reason) {
        return new Decision(
                new Saga(step, State.COMPENSATING, commands.size()), commands, Outcome.REJECTED, reason);
    }

    private static Decision compensationDone(Saga saga, String reply) {
        if (!List.of(PAYMENT_REFUNDED, STOCK_RELEASED, DELIVERY_CANCELLED).contains(reply)) {
            return Decision.ignore(saga);
        }
        var left = saga.pendingCompensations() - 1;
        var state = left <= 0 ? State.REJECTED : State.COMPENSATING;
        return new Decision(new Saga(saga.step(), state, Math.max(left, 0)), List.of(), null, null);
    }

    /** Where each command goes. */
    public static String topicOf(String command) {
        return switch (command) {
            case RESERVE_PAYMENT, REFUND_PAYMENT -> "payment.commands";
            case RESERVE_STOCK, RELEASE_STOCK -> "inventory.commands";
            case SCHEDULE_DELIVERY, CANCEL_DELIVERY -> "delivery.commands";
            default -> throw new IllegalArgumentException("Not a command: " + command);
        };
    }
}

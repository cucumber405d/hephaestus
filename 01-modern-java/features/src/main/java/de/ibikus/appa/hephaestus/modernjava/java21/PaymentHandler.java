package de.ibikus.appa.hephaestus.modernjava.java21;

import de.ibikus.appa.hephaestus.modernjava.java16.Money;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Approved;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Declined;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Pending;
import java.math.BigDecimal;

/**
 * Java 21 record patterns (JEP 440) and pattern matching for switch (JEP 441): nested deconstruction, guards with
 * {@code when}, and no {@code default} branch, because the sealed hierarchy makes the switch exhaustive. Adding a
 * fourth {@link PaymentResult} subtype breaks compilation here, which is the point.
 */
public final class PaymentHandler {

    private static final BigDecimal REVIEW_LIMIT = new BigDecimal("1000");

    private PaymentHandler() {}

    public static String handle(PaymentResult result) {
        return switch (result) {
            case Approved(var id, Money(var amount, var currency))
            when amount.compareTo(REVIEW_LIMIT) > 0 -> "REVIEW " + id;
            case Approved approved -> "OK " + approved.transactionId();
            case Declined(var reason) -> "DECLINED " + reason;
            case Pending(var retryAfter) -> "RETRY IN " + retryAfter.toSeconds() + "s";
        };
    }
}

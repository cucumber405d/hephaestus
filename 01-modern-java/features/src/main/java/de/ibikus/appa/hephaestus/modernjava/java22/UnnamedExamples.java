package de.ibikus.appa.hephaestus.modernjava.java22;

import de.ibikus.appa.hephaestus.modernjava.java16.OrderLine;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Approved;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Declined;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult.Pending;
import java.util.List;

/** Java 22 unnamed variables and patterns (JEP 456; preview in 21): {@code _} says "deliberately unused". */
public final class UnnamedExamples {

    private UnnamedExamples() {}

    public static int countLines(List<OrderLine> lines) {
        int count = 0;
        for (var _ : lines) {
            count++;
        }
        return count;
    }

    public static int quantityOrZero(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException _) {
            return 0;
        }
    }

    /** Several type patterns share one case when no bindings are needed. */
    public static boolean needsAction(PaymentResult result) {
        return switch (result) {
            case Approved _ -> false;
            case Declined _, Pending _ -> true;
        };
    }
}

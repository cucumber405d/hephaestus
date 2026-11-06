package de.ibikus.appa.hephaestus.modernjava.java15;

import java.math.BigDecimal;

/** Java 15 text blocks (JEP 378; preview in 13 and 14): incidental indentation is stripped automatically. */
public final class OrderTemplates {

    private static final String ORDER_JSON = """
            {
              "id": "%s",
              "total": %s
            }
            """;

    private OrderTemplates() {}

    public static String orderJson(String id, BigDecimal total) {
        return ORDER_JSON.formatted(id, total.toPlainString());
    }
}

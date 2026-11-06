package de.ibikus.appa.hephaestus.modernjava.java24;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.modernjava.java16.OrderLine;
import de.ibikus.appa.hephaestus.modernjava.java17.PaymentResult;
import org.junit.jupiter.api.Test;

class ClassInspectorTest {

    @Test
    void readsMethodNamesFromClassFileBytes() throws Exception {
        assertThat(ClassInspector.methodNames(OrderLine.class)).contains("total", "expandUnits", "describe");
    }

    @Test
    void handlesNestedClasses() throws Exception {
        assertThat(ClassInspector.methodNames(PaymentResult.Approved.class)).contains("transactionId", "amount");
    }
}

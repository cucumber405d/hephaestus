package de.ibikus.appa.hephaestus.modernjava.java17;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.random.RandomGeneratorFactory;
import org.junit.jupiter.api.Test;

class PaymentResultTest {

    @Test
    void hierarchyIsClosed() {
        assertThat(PaymentResult.class.isSealed()).isTrue();
        assertThat(Arrays.stream(PaymentResult.class.getPermittedSubclasses()).map(Class::getSimpleName))
                .containsExactlyInAnyOrder("Approved", "Declined", "Pending");
    }

    @Test
    void transactionIdIsSixteenHexChars() {
        var id = PaymentResult.newTransactionId(
                RandomGeneratorFactory.of("L64X128MixRandom").create(42));

        assertThat(id).hasSize(16).matches("[0-9a-f]{16}");
    }

    @Test
    void transactionIdIsReproducibleForSameSeed() {
        var factory = RandomGeneratorFactory.of("L64X128MixRandom");

        assertThat(PaymentResult.newTransactionId(factory.create(7)))
                .isEqualTo(PaymentResult.newTransactionId(factory.create(7)));
    }
}

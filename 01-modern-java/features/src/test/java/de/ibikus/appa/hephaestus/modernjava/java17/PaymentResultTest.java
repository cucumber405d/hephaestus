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

    /** Each byte uses the full 0-255 range: across many ids, high nibbles are not all zero. */
    @Test
    void transactionIdUsesFullByteRange() {
        var random = RandomGeneratorFactory.of("L64X128MixRandom").create(42);
        var highNibbles = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            var id = PaymentResult.newTransactionId(random);
            for (int c = 0; c < id.length(); c += 2) {
                highNibbles.append(id.charAt(c));
            }
        }

        assertThat(highNibbles.toString()).matches(".*[1-9a-f].*");
    }

    @Test
    void differentSeedsGiveDifferentIds() {
        var factory = RandomGeneratorFactory.of("L64X128MixRandom");

        assertThat(PaymentResult.newTransactionId(factory.create(1)))
                .isNotEqualTo(PaymentResult.newTransactionId(factory.create(2)));
    }

    @Test
    void transactionIdIsReproducibleForSameSeed() {
        var factory = RandomGeneratorFactory.of("L64X128MixRandom");

        assertThat(PaymentResult.newTransactionId(factory.create(7)))
                .isEqualTo(PaymentResult.newTransactionId(factory.create(7)));
    }
}

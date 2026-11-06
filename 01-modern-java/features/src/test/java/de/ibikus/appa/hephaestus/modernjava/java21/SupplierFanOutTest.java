package de.ibikus.appa.hephaestus.modernjava.java21;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class SupplierFanOutTest {

    /**
     * 10,000 blocking calls of 100 ms each. On 100 platform threads this would take ~10 s; virtual threads finish
     * in a few hundred ms. The 2 s limit leaves room for slow CI runners.
     */
    @Test
    @Tag("timing")
    void tenThousandBlockingCallsFinishQuicklyOnVirtualThreads() throws Exception {
        long start = System.nanoTime();

        var answers = SupplierFanOut.queryAll(10_000, Duration.ofMillis(100));

        var elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(answers).hasSize(10_000).contains("supplier-0", "supplier-9999");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void virtualThreadIsVirtual() throws Exception {
        var thread = Thread.ofVirtual().name("forge-", 0).start(() -> {});
        thread.join();

        assertThat(thread.isVirtual()).isTrue();
        assertThat(thread.getName()).isEqualTo("forge-0");
    }
}

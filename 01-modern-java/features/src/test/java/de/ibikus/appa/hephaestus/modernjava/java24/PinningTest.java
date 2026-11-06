package de.ibikus.appa.hephaestus.modernjava.java24;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.modernjava.java22.NativeSort;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Test;

/**
 * JEP 491 (Java 24): a virtual thread that blocks inside {@code synchronized} no longer pins its carrier thread.
 * Blocking while a native frame is on the stack still pins. Both claims are checked through the JFR event
 * {@code jdk.VirtualThreadPinned}.
 */
class PinningTest {

    private interface Blocking {
        void run() throws Throwable;
    }

    @Test
    void blockingInsideSynchronizedDoesNotPin() throws Exception {
        var lock = new Object();

        boolean pinned = pinnedWhileRunningOnVirtualThread(
                () -> {
                    synchronized (lock) {
                        Thread.sleep(50);
                    }
                },
                Duration.ofSeconds(2));

        assertThat(pinned).isFalse();
    }

    @Test
    void blockingInsideNativeFramePins() throws Exception {
        boolean pinned = pinnedWhileRunningOnVirtualThread(
                () -> NativeSort.sortDescending(new int[] {3, 1, 2}, comparison -> sleepQuietly(20)),
                Duration.ofSeconds(5));

        assertThat(pinned).isTrue();
    }

    /** Waits for the JFR event with a latch instead of a fixed sleep: delivery is asynchronous. */
    private static boolean pinnedWhileRunningOnVirtualThread(Blocking action, Duration wait) throws Exception {
        var pinnedEvent = new CountDownLatch(1);
        try (var stream = new RecordingStream()) {
            stream.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ZERO);
            stream.onEvent("jdk.VirtualThreadPinned", event -> pinnedEvent.countDown());
            stream.startAsync();

            var thread = Thread.ofVirtual().start(() -> {
                try {
                    action.run();
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            });
            thread.join();

            return pinnedEvent.await(wait.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

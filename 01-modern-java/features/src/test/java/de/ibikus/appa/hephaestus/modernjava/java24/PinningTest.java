package de.ibikus.appa.hephaestus.modernjava.java24;

import static org.assertj.core.api.Assertions.assertThat;

import de.ibikus.appa.hephaestus.modernjava.java22.NativeSort;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.Test;

/**
 * JEP 491 (Java 24): a virtual thread that blocks inside {@code synchronized} no longer pins its carrier thread.
 * Blocking while a native frame is on the stack still pins. Both claims are checked through the JFR event
 * {@code jdk.VirtualThreadPinned}, filtered by thread id, in one recording: the native-frame thread is the control
 * that proves events are delivered at all, so "no event for the synchronized thread" cannot pass vacuously.
 */
class PinningTest {

    private interface Blocking {
        void run() throws Throwable;
    }

    @Test
    void synchronizedDoesNotPinWhileNativeFrameDoes() throws Throwable {
        var lock = new Object();
        Set<Long> pinnedThreadIds = ConcurrentHashMap.newKeySet();

        try (var stream = new RecordingStream()) {
            stream.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ZERO);
            stream.onEvent("jdk.VirtualThreadPinned", event -> {
                var thread = event.getThread("eventThread");
                if (thread != null) {
                    pinnedThreadIds.add(thread.getJavaThreadId());
                }
            });
            stream.startAsync();

            long synchronizedThread = runOnVirtualThread(() -> {
                synchronized (lock) {
                    Thread.sleep(50);
                }
            });
            long nativeFrameThread = runOnVirtualThread(
                    () -> NativeSort.sortDescending(new int[] {3, 1, 2}, comparison -> sleepQuietly(20)));

            assertThat(awaitPinned(pinnedThreadIds, nativeFrameThread, Duration.ofSeconds(5)))
                    .as("control: blocking in a native frame pins and the event is delivered")
                    .isTrue();
            assertThat(pinnedThreadIds).as("synchronized no longer pins").doesNotContain(synchronizedThread);
        }
    }

    /** JFR delivers events asynchronously (about once per second): poll until the id shows up or time runs out. */
    private static boolean awaitPinned(Set<Long> pinnedThreadIds, long threadId, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (pinnedThreadIds.contains(threadId)) {
                return true;
            }
            Thread.sleep(50);
        }
        return pinnedThreadIds.contains(threadId);
    }

    /** Runs {@code action} on a new virtual thread, rethrows its failure, returns the thread id. */
    private static long runOnVirtualThread(Blocking action) throws Throwable {
        var failure = new AtomicReference<Throwable>();
        var thread = Thread.ofVirtual().unstarted(() -> {
            try {
                action.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        thread.start();
        thread.join();
        if (failure.get() != null) {
            throw failure.get();
        }
        return thread.threadId();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

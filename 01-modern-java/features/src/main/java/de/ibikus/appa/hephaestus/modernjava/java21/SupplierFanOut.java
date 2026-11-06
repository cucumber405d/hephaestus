package de.ibikus.appa.hephaestus.modernjava.java21;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Java 21 virtual threads (JEP 444; preview in 19 and 20): one cheap thread per blocking task, written in plain
 * blocking style. While a virtual thread sleeps or waits on I/O, its carrier thread runs other virtual threads.
 */
public final class SupplierFanOut {

    private SupplierFanOut() {}

    /** Queries {@code suppliers} slow suppliers concurrently; each call blocks for {@code latency}. */
    public static List<String> queryAll(int suppliers, Duration latency)
            throws InterruptedException, ExecutionException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<String>>(suppliers);
            for (int i = 0; i < suppliers; i++) {
                int supplier = i;
                futures.add(executor.submit(() -> {
                    Thread.sleep(latency);
                    return "supplier-" + supplier;
                }));
            }
            var answers = new ArrayList<String>(suppliers);
            for (var future : futures) {
                answers.add(future.get());
            }
            return answers;
        }
    }
}

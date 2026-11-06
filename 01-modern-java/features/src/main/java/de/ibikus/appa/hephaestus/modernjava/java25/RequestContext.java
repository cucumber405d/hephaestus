package de.ibikus.appa.hephaestus.modernjava.java25;

import java.util.concurrent.Callable;

/**
 * Java 25 scoped values (JEP 506; preview in 21-24): an immutable, per-call context that is visible down the call
 * chain and disappears when the call returns. Unlike {@code ThreadLocal} it cannot be mutated, cannot leak into
 * the next task of a pooled thread and costs nothing to "copy" for millions of virtual threads.
 */
public record RequestContext(String tenant, String traceId) {

    private static final ScopedValue<RequestContext> CURRENT = ScopedValue.newInstance();

    /** Runs {@code work} with {@code context} bound; nested calls shadow the outer binding until they return. */
    public static <T> T callWith(RequestContext context, Callable<T> work) throws Exception {
        return ScopedValue.where(CURRENT, context).call(work::call);
    }

    public static RequestContext current() {
        return CURRENT.orElseThrow(() -> new IllegalStateException("No request context"));
    }
}

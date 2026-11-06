package de.ibikus.appa.hephaestus.modernjava.java25;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class RequestContextTest {

    private static final RequestContext ACME = new RequestContext("acme", "t-1");
    private static final RequestContext GLOBEX = new RequestContext("globex", "t-2");

    @Test
    void contextIsVisibleInsideTheCall() throws Exception {
        assertThat(RequestContext.callWith(ACME, () -> RequestContext.current().tenant()))
                .isEqualTo("acme");
    }

    @Test
    void contextIsVisibleDeepInTheCallChainWithoutParameters() throws Exception {
        assertThat(RequestContext.callWith(ACME, RequestContextTest::auditLine)).isEqualTo("[t-1] acme");
    }

    @Test
    void noContextOutsideTheCall() throws Exception {
        RequestContext.callWith(ACME, () -> "done");

        assertThatThrownBy(RequestContext::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No request context");
    }

    @Test
    void nestedBindingShadowsAndThenRestores() throws Exception {
        var seen = RequestContext.callWith(ACME, () -> {
            var inner = RequestContext.callWith(
                    GLOBEX, () -> RequestContext.current().tenant());
            return inner + "/" + RequestContext.current().tenant();
        });

        assertThat(seen).isEqualTo("globex/acme");
    }

    /** Counterexample: a ThreadLocal set by one task leaks into the next task on a pooled thread. */
    @Test
    void threadLocalLeaksIntoTheNextTaskOnAPooledThread() throws Exception {
        var tenant = new ThreadLocal<String>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> tenant.set("acme")).get(); // forgot remove()

            assertThat(pool.submit(tenant::get).get()).isEqualTo("acme");
        }
    }

    /** Pair to the ThreadLocal counterexample: a scoped binding is gone for the next task of the same thread. */
    @Test
    void scopedValueDoesNotLeakIntoTheNextTaskOnAPooledThread() throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> RequestContext.callWith(ACME, () -> "done")).get();

            var tenantSeenByNextTask = pool.submit(() -> {
                        try {
                            return RequestContext.current().tenant();
                        } catch (IllegalStateException e) {
                            return "none";
                        }
                    })
                    .get();

            assertThat(tenantSeenByNextTask).isEqualTo("none");
        }
    }

    private static String auditLine() {
        var context = RequestContext.current();
        return "[" + context.traceId() + "] " + context.tenant();
    }
}

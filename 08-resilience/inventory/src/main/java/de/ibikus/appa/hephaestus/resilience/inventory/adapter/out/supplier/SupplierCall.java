package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierOffer;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.springframework.core.retry.RetryException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * One raw supplier call shared by all mechanisms. A 404 becomes {@link UnknownOfferException} right here: it is an
 * answer, so no mechanism may retry it or count it as a failure. Timeouts, refused connections and 5xx stay as they
 * are for the mechanisms to retry and count ({@link #isFailure(Throwable)}).
 */
@Component
public class SupplierCall {

    private final SupplierHttpApi api;

    public SupplierCall(SupplierHttpApi api) {
        this.api = api;
    }

    public SupplierOffer fetch(String sku) {
        try {
            return api.offer(sku).toDomain();
        } catch (HttpClientErrorException.NotFound e) {
            throw new UnknownOfferException(sku);
        }
    }

    /** What all mechanisms treat as "the supplier failed" (retried, counted by circuit breakers). */
    public static boolean isFailure(Throwable t) {
        return t instanceof ResourceAccessException || t instanceof HttpServerErrorException;
    }

    /**
     * Turns whatever stopped the call into the port's vocabulary: an unknown offer stays itself; another 4xx means
     * we sent something the supplier does not accept (our bug: a 500 is right); everything else (failures and
     * refusals by a mechanism: circuit open, bulkhead full, rate or time limit) means "supplier unavailable".
     */
    public static RuntimeException translate(String mechanism, String sku, Throwable t) {
        // Time limiters (other thread) and Spring's retry may wrap what the call threw
        if ((t instanceof ExecutionException || t instanceof CompletionException || t instanceof RetryException)
                && t.getCause() != null) {
            return translate(mechanism, sku, t.getCause());
        }
        if (t instanceof UnknownOfferException || t instanceof SupplierUnavailableException) {
            return (RuntimeException) t;
        }
        if (t instanceof HttpClientErrorException e) {
            return new IllegalStateException("The supplier rejected our request for " + sku + ": " + e.getStatusCode(), e);
        }
        return new SupplierUnavailableException(mechanism + ": no answer for " + sku + " (" + t + ")", t);
    }
}

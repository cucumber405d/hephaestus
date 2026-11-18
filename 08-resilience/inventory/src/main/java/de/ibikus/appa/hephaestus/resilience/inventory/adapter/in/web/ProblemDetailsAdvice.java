package de.ibikus.appa.hephaestus.resilience.inventory.adapter.in.web;

import de.ibikus.appa.hephaestus.resilience.inventory.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.InvalidValueException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.StockItemNotFoundException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.SupplierUnavailableException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownOfferException;
import de.ibikus.appa.hephaestus.resilience.inventory.domain.UnknownSkuException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** RFC 9457 Problem Details with stable type URIs; machine-readable extras (sku, available) for callers. */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    static final String TYPE_BASE = "https://hephaestus.ibikus.de/problems/";

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail insufficientStock(InsufficientStockException ex) {
        var problem = problem(HttpStatus.CONFLICT, "insufficient-stock", "Insufficient stock", ex.getMessage());
        problem.setProperty("sku", ex.sku());
        problem.setProperty("available", ex.available());
        return problem;
    }

    @ExceptionHandler(UnknownSkuException.class)
    ProblemDetail unknownSku(UnknownSkuException ex) {
        var problem = problem(HttpStatusCode.valueOf(422), "unknown-sku", "Unknown SKU", ex.getMessage());
        problem.setProperty("sku", ex.sku());
        return problem;
    }

    @ExceptionHandler(StockItemNotFoundException.class)
    ProblemDetail notFound(StockItemNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", ex.getMessage());
    }

    @ExceptionHandler(UnknownOfferException.class)
    ProblemDetail unknownOffer(UnknownOfferException ex) {
        var problem = problem(HttpStatus.NOT_FOUND, "unknown-offer", "No supplier offer", ex.getMessage());
        problem.setProperty("sku", ex.sku());
        return problem;
    }

    /** No live answer and nothing cached: try again once a circuit breaker could have closed. */
    @ExceptionHandler(SupplierUnavailableException.class)
    ResponseEntity<ProblemDetail> supplierUnavailable(SupplierUnavailableException ex) {
        LOG.warn("Supplier unavailable: {}", ex.getMessage());
        var problem = problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "supplier-unavailable",
                "Supplier unavailable",
                "The supplier cannot answer right now and no earlier offer is known");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "10")
                .body(problem);
    }

    @ExceptionHandler(InvalidValueException.class)
    ProblemDetail invalid(InvalidValueException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage());
    }

    /** Last resort: details stay in the log. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal error",
                "The request could not be processed; the error has been logged");
    }

    /** Spring's own problems (validation, malformed JSON, unsupported API version, 405...) get a type URI. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null
                && response.getBody() instanceof ProblemDetail problem
                && (problem.getType() == null
                        || "about:blank".equals(problem.getType().toString()))) {
            problem.setType(URI.create(TYPE_BASE + (status.value() == 400 ? "invalid-request" : "error")));
        }
        return response;
    }

    static ProblemDetail problem(HttpStatusCode status, String slug, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        return problem;
    }
}

package de.ibikus.appa.hephaestus.servicecommunication.ordering.adapter.in.web;

import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.InsufficientStockException;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.InvalidValueException;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.InventoryUnavailableException;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.OrderNotFoundException;
import de.ibikus.appa.hephaestus.servicecommunication.ordering.domain.UnknownItemException;
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

/** Ordering's own errors; Inventory's status codes never leak through unchanged. */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    static final String TYPE_BASE = "https://hephaestus.ibikus.de/problems/";
    static final String RETRY_AFTER_SECONDS = "5";

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail insufficientStock(InsufficientStockException ex) {
        var problem = problem(HttpStatus.CONFLICT, "insufficient-stock", "Insufficient stock", ex.getMessage());
        problem.setProperty("sku", ex.sku());
        return problem;
    }

    @ExceptionHandler(UnknownItemException.class)
    ProblemDetail unknownItem(UnknownItemException ex) {
        var problem = problem(HttpStatusCode.valueOf(422), "unknown-item", "Unknown item", ex.getMessage());
        problem.setProperty("sku", ex.sku());
        return problem;
    }

    /**
     * 503, not 502/504: we are the ones who cannot serve the request right now, and Retry-After tells a client
     * when trying again makes sense. The cause (timeout, refused connection) stays in our log.
     */
    @ExceptionHandler(InventoryUnavailableException.class)
    ResponseEntity<ProblemDetail> inventoryUnavailable(InventoryUnavailableException ex) {
        LOG.warn("{}: {}", ex.getMessage(), ex.getCause() == null ? "" : ex.getCause().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "inventory-unavailable",
                        "Inventory unavailable",
                        "Stock cannot be checked right now; try again later"));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail notFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidValueException.class)
    ProblemDetail invalid(InvalidValueException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal error",
                "The request could not be processed; the error has been logged");
    }

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
